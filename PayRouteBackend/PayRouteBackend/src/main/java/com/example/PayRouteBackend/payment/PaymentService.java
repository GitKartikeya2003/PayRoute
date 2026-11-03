package com.example.PayRouteBackend.payment;


import com.example.PayRouteBackend.bank.MockBankService;
import com.example.PayRouteBackend.common.ApiException;
import com.example.PayRouteBackend.common.Idempotency;
import com.example.PayRouteBackend.common.Ids;
import com.example.PayRouteBackend.kafka.EventService;
import com.example.PayRouteBackend.merchant.Merchant;
import com.example.PayRouteBackend.merchant.MerchantService;
import com.example.PayRouteBackend.order.OrderService;
import com.example.PayRouteBackend.order.PaymentOrder;
import com.example.PayRouteBackend.transaction.LedgerService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MerchantService merchantService;
    private final OrderService orderService;
    private final MockBankService bank;
    private final EventService event;
    private final LedgerService ledger;


    public PaymentService(PaymentRepository paymentRepository, MerchantService merchantService, OrderService orderService, MockBankService bank, EventService event, LedgerService ledger) {
        this.paymentRepository = paymentRepository;
        this.merchantService = merchantService;
        this.orderService = orderService;
        this.bank = bank;
        this.event = event;
        this.ledger = ledger;
    }


    public static long fee(long amount) {

        return (Math.multiplyExact(amount, 2) + 99) / 100;


    }


    @Transactional
    public Payment create(String merchantId, String key, PaymentDtos.Create r) {

        Idempotency.validate(key);
        merchantService.lock(merchantId);
        Payment existing = paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, key).orElse(null);
        if (existing != null) {

            if (!existing.orderId.equals(r.orderId())
                    || !existing.method.equals(r.method().name())
                    || !existing.scenario.equals(r.scenario().name())) {

                throw ApiException.conflict(
                        "Idempotency Key reused with different request"
                );
            }

            return existing;

        }

        PaymentOrder order = orderService.get(merchantId, r.orderId());

        if (order.status.equals("PAID")
                || paymentRepository.existsByOrderIdAndStatusIn(order.id, List.of("CREATED", "PENDING", "SUCCESS"))) {

            throw ApiException.conflict("Order already paid or has and Active payment");
        }
        Payment payment = new Payment();
        payment.id = Ids.next("pay");

        payment.merchantId = merchantId;
        payment.orderId = order.id;
        payment.amount = order.amount;
        payment.idempotencyKey = key;
        payment.method = r.method().name();
        payment.scenario = r.scenario().name();
        return paymentRepository.save(payment);
    }


    public Payment get(String merchantId, String id) {

        Payment p = paymentRepository.findById(id).orElseThrow(ApiException::missing);

        if (!p.merchantId.equals(merchantId)) throw ApiException.missing();
        return p;
    }

    @Transactional
    public Payment process(String merchantId, String key) {

        Merchant merchant = merchantService.lock(merchantId);
        Payment p = get(merchantId, key);

        if (!p.status.equals("CREATED")) return p;

        MockBankService.Result result = bank.initiate(p.scenario);

        p.resolvedAt = result.resolvedAt();
        finish(merchant, p, result.status());
        return p;

    }


    private void finish(Merchant m, Payment p, String status) {

        p.status = status;
        if (status.equals("SUCCESS")) {

            p.fee = fee(p.amount);
            orderService.get(m.id, p.orderId).status = "PAID";
            ledger.record(m, p.id, "PAYMENT", p.amount - p.fee);

            event.create(m.id, "payment.succeeded", p.id, p.amount);


        } else if (status.equals("FAILED")) {

            event.create(m.id, "payment.failed", p.id, p.amount);
        }
    }


    @Transactional
    public Payment reconcile(String merchantId, String id) {

        Merchant merchant = merchantService.lock(merchantId);
        Payment p = get(merchantId, id);

        if (p.status.equals("PENDING")) {

            finish(merchant, p, bank.check(p.resolvedAt));
        }
        return p;
    }

    public List<Payment> list(String m) {
        return paymentRepository.findByMerchantIdOrderByCreatedAtDesc(m);
    }


}
