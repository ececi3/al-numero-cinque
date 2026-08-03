package com.alnumerocinque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling abilita il tick periodico di OutboxPublisher (publisher
 * outbox -> Kafka/Redpanda), disattivato nel profilo test (vedi
 * app.eventi-async.enabled in application-test.yml).
 */
@SpringBootApplication
@EnableScheduling
public class AlNumeroCinqueApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlNumeroCinqueApplication.class, args);
    }
}
