package com.example.PayRouteBackend.kafka;


import com.example.PayRouteBackend.webhooks.WebhookService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka-enabled", havingValue = "true")
public class EventListener {

    private final EventConsumerService service;
    private final WebhookService webhookService;

    public EventListener(EventConsumerService s, WebhookService webhookService) {
        service = s;
        this.webhookService = webhookService;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC)
    public void onEvent(String eventId) {
        String webhookId =service.consume(eventId);

        if(webhookId != null) {

            webhookService.deliver(webhookId);
        }
    }
}
