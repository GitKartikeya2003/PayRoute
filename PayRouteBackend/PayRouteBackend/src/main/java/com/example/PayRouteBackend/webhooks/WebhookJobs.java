package com.example.PayRouteBackend.webhooks;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true")
public class WebhookJobs {
    private final WebhookRepository webhookRepository;
    private final WebhookService webhookService;

    public WebhookJobs(WebhookRepository webhookRepository, WebhookService webhookService) {
        this.webhookRepository = webhookRepository;
        this.webhookService = webhookService;
    }

    @Scheduled(fixedDelay = 3000)
    public void deliverPending() {
        for (Webhook w : webhookRepository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc("PENDING", Instant.now())) {
            webhookService.deliver(w.id);
        }
    }
}
