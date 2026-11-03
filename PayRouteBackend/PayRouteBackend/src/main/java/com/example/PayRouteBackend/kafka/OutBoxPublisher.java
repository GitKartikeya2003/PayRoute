package com.example.PayRouteBackend.kafka;


import jakarta.transaction.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.kafka-enabled", havingValue = "true")
public class OutBoxPublisher {

    private final EventRepository eventRepository;
    private final KafkaTemplate<String, String> kafka;


    public OutBoxPublisher(EventRepository eventRepository, KafkaTemplate<String, String> kafka) {
        this.eventRepository = eventRepository;
        this.kafka = kafka;
    }


    @Transactional
    public void publish(String id) throws Exception {

        Event e = eventRepository.lockById(id).orElseThrow();

        if (e.publishedAt != null) {
            return;
        }

        kafka.send(KafkaConfig.TOPIC, e.merchantId, e.id)
                .get(12, TimeUnit.SECONDS);

        e.publishedAt = Instant.now();


    }
}
