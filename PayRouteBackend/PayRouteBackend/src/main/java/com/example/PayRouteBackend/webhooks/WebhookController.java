package com.example.PayRouteBackend.webhooks;

import java.util.List;

import com.example.PayRouteBackend.merchant.MerchantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    private final WebhookService service;
    private final MerchantService merchants;

    Logger logger = LoggerFactory.getLogger(WebhookController.class);

    public WebhookController(WebhookService s, MerchantService m) {
        service = s;
        merchants = m;
    }

    @GetMapping
    public List<Webhook> list(@RequestHeader("X-Api-Key") String k) {
        return service.list(merchants.authenticate(k).id);
    }

    @PostMapping("/{id}/retry")
    public Webhook retry(@RequestHeader("X-Api-Key") String k, @PathVariable String id) {
        return service.retry(merchants.authenticate(k).id, id);
    }


    // Add this method inside WebhookController.java:
    @PostMapping("/test-receiver")
    public void receiveTestWebhook(
            @RequestHeader(value = "X-PayRoute-Event", required = false) String eventId,
            @RequestHeader(value = "X-PayRoute-Signature", required = false) String signature,
            @RequestBody String payload
    ) {

        logger.info("Received Webhook Event: {} | Signature: {}", eventId, signature);
        logger.info("Payload: {}", payload);
    }
}
