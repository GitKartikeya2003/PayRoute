package com.example.PayRouteBackend.webhooks;


import com.example.PayRouteBackend.common.ApiException;
import com.example.PayRouteBackend.kafka.Event;
import com.example.PayRouteBackend.kafka.EventRepository;
import com.example.PayRouteBackend.merchant.Merchant;
import com.example.PayRouteBackend.merchant.MerchantRepository;
import com.example.PayRouteBackend.merchant.MerchantService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final EventRepository eventRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;


    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebhookService(WebhookRepository webhookRepository, EventRepository eventRepository
            , MerchantRepository merchantRepository, MerchantService merchantService) {

        this.webhookRepository = webhookRepository;
        this.eventRepository = eventRepository;
        this.merchantRepository = merchantRepository;
        this.merchantService = merchantService;

    }


    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void deliver(String id) {
        Webhook w = webhookRepository.lockById(id).orElseThrow();
        if (!w.status.equals("PENDING") || w.nextAttemptAt.isAfter(Instant.now())) return;
        Event e = eventRepository.findById(w.eventId).orElseThrow();
        Merchant m = merchantRepository.findById(w.merchantId).orElseThrow();
        w.attempts++;
        try {
            merchantService.validateWebHookUrl(w.url);
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(w.url))
                            .timeout(Duration.ofSeconds(3))
                            .header("Content-Type", "application/json")
                            .header("X-PayRoute-Event", e.id)
                            .header("X-PayRoute-Signature", sign(m.webhookSecret, e.payload))
                            .POST(HttpRequest.BodyPublishers.ofString(e.payload))
                            .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
            w.status = "DELIVERED";
            w.lastError = null;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            w.lastError =
                    ex instanceof IllegalStateException ? ex.getMessage() : ex.getClass().getSimpleName();
            w.status = w.attempts >= 5 ? "FAILED" : "PENDING";
            w.nextAttemptAt = Instant.now().plusSeconds(1L << w.attempts);
        }
    }


    @Transactional
    public Webhook retry(String merchantId, String id) {
        Webhook w = webhookRepository.lockById(id).orElseThrow(ApiException::missing);
        if (!w.merchantId.equals(merchantId)) throw ApiException.missing();
        if (w.status.equals("DELIVERED")) throw ApiException.conflict("Webhook already delivered");
        Merchant m = merchantRepository.findById(merchantId).orElseThrow();
        if (m.webhookUrl == null || m.webhookUrl.isBlank())
            throw ApiException.bad("Configure a webhook URL first");
        w.url = m.webhookUrl;
        w.status = "PENDING";
        w.attempts = 0;
        w.nextAttemptAt = Instant.now();
        w.lastError = null;
        deliver(w.id);
        return w;
    }


    public List<Webhook> list(String m) {
        return webhookRepository.findByMerchantIdOrderByCreatedAtDesc(m);
    }

}
