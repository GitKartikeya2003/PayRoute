package com.example.PayRouteBackend.transaction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {




    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LedgerEntry e where e.id = :id")
    Optional<LedgerEntry> lockByid(@Param("id") String id);

    List<LedgerEntry> findByMerchantIdOrderByCreatedAtDesc(String merchantId);


}
