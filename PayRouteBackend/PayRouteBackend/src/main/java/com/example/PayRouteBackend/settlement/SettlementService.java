package com.example.PayRouteBackend.settlement;

import com.example.PayRouteBackend.common.ApiException;
import com.example.PayRouteBackend.common.Idempotency;
import com.example.PayRouteBackend.common.Ids;
import com.example.PayRouteBackend.kafka.EventService;
import com.example.PayRouteBackend.merchant.Merchant;
import com.example.PayRouteBackend.merchant.MerchantService;
import com.example.PayRouteBackend.transaction.LedgerService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SettlementService {

    private final SettlementRepository repo;

    private final MerchantService merchants;
    private final LedgerService ledger;
    private final EventService event;


    public SettlementService(SettlementRepository repo, MerchantService merchants, LedgerService ledger, EventService event) {
        this.repo = repo;
        this.merchants = merchants;
        this.ledger = ledger;
        this.event = event;
    }

    @Transactional
    public Settlement create(String m, String key) {
        Idempotency.validate(key);
        Merchant merchant = merchants.lock(m);
        Settlement old = repo.findByMerchantIdAndIdempotencyKey(m, key).orElse(null);
        if (old != null) return old;
        if (merchant.balance < 0) throw ApiException.conflict("No available balance to settle");
        Settlement s = new Settlement();
        s.id = Ids.next("stl");
        s.merchantId = m;
        s.idempotencyKey = key;
        s.amount = merchant.balance;
        repo.save(s);
        ledger.record(merchant, s.id, "SETTLEMENT", -s.amount);
        event.create(m, "settlement.created", s.id, s.amount);
        return s;
    }

    public List<Settlement> list(String s) {
        return repo.findByMerchantIdOrderByCreatedAtDesc(s);
    }
}
