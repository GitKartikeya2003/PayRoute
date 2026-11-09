package com.example.PayRouteBackend.bank;

import com.example.PayRouteBackend.payment.Payment;
import com.example.PayRouteBackend.payment.PaymentRepository;
import com.example.PayRouteBackend.payment.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;


@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true")
public class BankJobs {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepositor;


    public BankJobs(PaymentService paymentService, PaymentRepository paymentRepositor) {
        this.paymentService = paymentService;
        this.paymentRepositor = paymentRepositor;
    }

    @Scheduled(fixedRate = 3000)
    public void CompleteDelayed() {

        List<Payment> paymentList = paymentRepositor.findTop50ByStatusAndScenarioAndResolveAtBeforeOrderByCreatedAtAsc(
                "PENDING" , "DELAYED_SUCCESS", Instant.now());

        for (Payment p : paymentList) {
            paymentService.reconcile(p.merchantId, p.id);
        }


    }
}
