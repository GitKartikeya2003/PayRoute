package com.example.PayRouteBackend.settlement;

import com.example.PayRouteBackend.merchant.MerchantService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final MerchantService merchantService;

    public SettlementController(SettlementService settlementService, MerchantService merchantService) {
        this.settlementService = settlementService;
        this.merchantService = merchantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Settlement create(@RequestHeader("X-Api-Key") String key,
                             @RequestHeader("Idempotency-Key") String ik) {
        return settlementService.create(merchantService.authenticate(key).id, ik);
    }

    @GetMapping
    public List<Settlement> list(@RequestHeader("X-Api-Key") String key) {
        return settlementService.list(merchantService.authenticate(key).id);
    }
}