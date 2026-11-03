package com.example.PayRouteBackend.transaction;


import com.example.PayRouteBackend.merchant.MerchantService;
import com.example.PayRouteBackend.payment.Payment;
import com.example.PayRouteBackend.payment.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final MerchantService merchantService;
    private final PaymentService paymentService;


    public TransactionController(TransactionService transactionService, MerchantService merchantService, PaymentService paymentService) {
        this.transactionService = transactionService;
        this.merchantService = merchantService;
        this.paymentService = paymentService;
    }


    @GetMapping
    public List<Payment> list(@RequestHeader("X-Api-Key") String key,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to) {
        return transactionService.list(merchantService.authenticate(key).id, status, from, to);
    }


    @GetMapping("/ledger")
    public List<LedgerEntry> ledger(@RequestHeader("X-Api-Key") String key) {
        return transactionService.ledger(merchantService.authenticate(key).id);
    }


    @GetMapping("/{id}")
    public Payment details(@RequestHeader("X-Api-Key") String key, @PathVariable String id) {
        return paymentService.get(merchantService.authenticate(key).id, id);
    }


}
