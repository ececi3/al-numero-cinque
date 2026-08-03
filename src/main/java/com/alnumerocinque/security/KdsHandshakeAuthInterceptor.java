package com.alnumerocinque.security;

import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.service.TokenRevocatoService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Autentica l'handshake WebSocket del KDS con lo stesso JWT usato per le API
 * REST. Il token puo' arrivare via header Authorization (client STOMP nativi
 * che lo supportano) o via query param access_token (fallback per client,
 * come un WebSocket nativo da browser, che non possono impostare header
 * custom sull'handshake). Richiede ruolo CUCINA, coerente con /api/kds/**.
 */
@Component
public class KdsHandshakeAuthInterceptor implements HandshakeInterceptor {

    private static final String PREFISSO_BEARER = "Bearer ";
    private static final String QUERY_PARAM_TOKEN = "access_token";

    private final JwtService jwtService;
    private final TokenRevocatoService tokenRevocatoService;

    public KdsHandshakeAuthInterceptor(JwtService jwtService, TokenRevocatoService tokenRevocatoService) {
        this.jwtService = jwtService;
        this.tokenRevocatoService = tokenRevocatoService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = estraiToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtService.valida(token).getPayload();
            if (tokenRevocatoService.isRevocato(claims.getId())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            RuoloUtente ruolo = RuoloUtente.valueOf(claims.get("ruolo", String.class));
            if (ruolo != RuoloUtente.CUCINA) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put("utenteId", claims.get("utenteId", Long.class));
            attributes.put("username", claims.getSubject());
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }

    private String estraiToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith(PREFISSO_BEARER)) {
            return header.substring(PREFISSO_BEARER.length());
        }

        String query = request.getURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && QUERY_PARAM_TOKEN.equals(param.substring(0, eq))) {
                return param.substring(eq + 1);
            }
        }
        return null;
    }
}
