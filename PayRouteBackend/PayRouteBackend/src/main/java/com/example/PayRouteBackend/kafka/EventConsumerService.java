package com.example.PayRouteBackend.kafka;


import com.example.PayRouteBackend.common.Ids;
import com.example.PayRouteBackend.merchant.Merchant;
import com.example.PayRouteBackend.merchant.MerchantRepository;
import com.example.PayRouteBackend.webhooks.Webhook;
import com.example.PayRouteBackend.webhooks.WebhookRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EventConsumerService {


    private final EventRepository eventRepository;
    private final WebhookRepository webhooks;

    private final MerchantRepository merchantRepository;


    public EventConsumerService(EventRepository eventRepository, WebhookRepository webhooks, MerchantRepository merchantRepository) {
        this.eventRepository = eventRepository;
        this.webhooks = webhooks;
        this.merchantRepository = merchantRepository;
    }


    @Transactional
    public String consume(String id) {


        Event event = eventRepository.findById(id).orElseThrow();

        if(event.consumedAt!= null) return null;

        Merchant m = merchantRepository.findById(event.merchantId).orElseThrow();

        Webhook w = new Webhook();
        w.id = Ids.next("wh");
        w.eventId = event.id;
        w.merchantId = m.id;
        w.url = m.webhookUrl;

        if(w.url == null || w.url.isBlank()) {

            w.status = "SKIPPED";
            w.lastError = "No Webhook URL provided";
        }

        webhooks.save(w);
        event.consumedAt = Instant.now();
        return w.status.equals("PENDING") ? w.id : null;


    }
}
