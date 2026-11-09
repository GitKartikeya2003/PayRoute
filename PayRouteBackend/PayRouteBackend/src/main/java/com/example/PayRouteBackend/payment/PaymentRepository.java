package com.example.PayRouteBackend.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Payment e where e.id = :id")
    Optional<Payment> lockById(@Param("id") String id);


    Optional<Payment> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId);

    boolean existsByOrderIdAndStatusIn(String orderId, List<String> statuses);


    List<Payment> findTop50ByStatusAndScenarioAndResolveAtBeforeOrderByCreatedAtAsc(
            String status, String scenario, Instant now);


}
