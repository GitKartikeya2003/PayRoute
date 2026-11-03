package com.example.PayRouteBackend.refund;


import com.example.PayRouteBackend.common.ApiException;
import com.example.PayRouteBackend.common.Idempotency;
import com.example.PayRouteBackend.common.Ids;
import com.example.PayRouteBackend.kafka.EventService;
import com.example.PayRouteBackend.merchant.Merchant;
import com.example.PayRouteBackend.merchant.MerchantService;
import com.example.PayRouteBackend.payment.Payment;
import com.example.PayRouteBackend.payment.PaymentService;
import com.example.PayRouteBackend.transaction.LedgerService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RefundService {


    private final RefundRepository refundRepository;
    private final MerchantService merchantService;
    private final PaymentService paymentService;
    private final EventService event;
    private final LedgerService ledgerService;


    public RefundService(RefundRepository refundRepository
            , MerchantService merchantService, PaymentService paymentService
            , EventService event, LedgerService ledgerService) {

        this.refundRepository = refundRepository;
        this.merchantService = merchantService;
        this.paymentService = paymentService;
        this.event = event;
        this.ledgerService = ledgerService;
    }


    public Refund create(String merchantId, String key, RefundDtos.Create request) {


        Idempotency.validate(key);

        Merchant merchant = merchantService.lock(merchantId);
        Refund old = refundRepository.findByMerchantIdAndIdempotencyKey(merchantId, key).orElse(null);


        if (old != null) {

            if (old.paymentID.equals(request.paymentId()) || old.amount != request.amount()) {

                throw ApiException.conflict("Idempotency key reused with different refund");
            }

            return old;
        }

        Payment p = paymentService.get(merchantId, request.paymentId());

        if (!p.status.equals("SUCCESS")) {
            throw ApiException.conflict("Only success payment can be refunded");
        }

        if (request.amount() < 1 || request.amount() > p.amount - p.refunded) {
            throw ApiException.conflict("Refund exceeds remaining payment amount");
        }


        long before = p.refunded;
        long after = before + request.amount();
        long feeReturned = (p.fee * after / p.amount) - (p.fee * before / p.amount);
        long debit = request.amount() - feeReturned;

        if (merchant.balance < debit) {

            throw ApiException.conflict("Insufficient balance");
        }

        Refund r = new Refund();

        r.id = Ids.next("rfnd");
        r.merchantId = merchantId;
        r.paymentID = request.paymentId();
        r.idempotencyKey = key;
        r.amount = request.amount();
        p.refunded = after;
        refundRepository.save(r);
        ledgerService.record(merchant, r.id, "REFUND", -debit);
        event.create(merchantId, "refund.succeeded", r.id, r.amount);
        return r;

    }

    public List<Refund> list(String s) {
        return refundRepository.findByMerchantIdOrderByCreatedAtDesc(s);
    }

}
