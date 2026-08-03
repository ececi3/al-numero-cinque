package com.alnumerocinque.service;

import com.alnumerocinque.domain.OutboxEvent;
import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.domain.Utente;
import com.alnumerocinque.repository.OutboxEventRepository;
import com.alnumerocinque.repository.UtenteRepository;
import com.alnumerocinque.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifica la pipeline completa end-to-end: scrittura in outbox_event ->
 * OutboxPublisher la pubblica su Kafka (broker embedded) -> KdsWebSocketBridge
 * la consuma e la ripubblica sul canale STOMP /topic/kds -> un client
 * WebSocket autenticato come CUCINA la riceve. Sovrascrive
 * app.eventi-async.enabled e riattiva KafkaAutoConfiguration (esclusa di
 * default nel profilo test) solo per questa classe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.eventi-async.enabled=true",
        "app.outbox-publisher.intervallo-ms=200"
})
@EmbeddedKafka(partitions = 1, topics = "al-numero-cinque.eventi-dominio")
@ActiveProfiles("test")
class EventiAsyncIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UtenteRepository utenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void eventoOutbox_vienePubblicatoSuKafkaERipubblicatoSulTopicKds() throws Exception {
        Utente cucina = utenteRepository.save(
                new Utente("cucina.ws-test", passwordEncoder.encode("password-123"), RuoloUtente.CUCINA));
        String token = jwtService.generaToken(cucina);

        BlockingQueue<String> messaggiRicevuti = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/topic/kds", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        messaggiRicevuti.add((String) payload);
                    }
                });
            }
        };

        String url = "ws://localhost:" + port + "/ws-kds?access_token=" + token;
        StompSession session = stompClient.connectAsync(url, handler).get(5, TimeUnit.SECONDS);

        try {
            OutboxEvent evento = outboxEventRepository.save(
                    new OutboxEvent("GRUPPO_INVIO", "999", "GRUPPO_IN_CODA", "{\"gruppoInvioId\":999}"));

            String ricevuto = messaggiRicevuti.poll(10, TimeUnit.SECONDS);
            assertThat(ricevuto).isEqualTo("{\"gruppoInvioId\":999}");

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(outboxEventRepository.findById(evento.getId()).orElseThrow().isPubblicato()).isTrue());
        } finally {
            session.disconnect();
        }
    }

    @Test
    void handshakeWebSocket_senzaRuoloCucina_vieneRifiutato() {
        Utente cameriere = utenteRepository.save(
                new Utente("cameriere.ws-test", passwordEncoder.encode("password-123"), RuoloUtente.CAMERIERE));
        String token = jwtService.generaToken(cameriere);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        String url = "ws://localhost:" + port + "/ws-kds?access_token=" + token;

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS));
    }
}
