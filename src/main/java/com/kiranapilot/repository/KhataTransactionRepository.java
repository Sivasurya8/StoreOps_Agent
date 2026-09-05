package com.kiranapilot.repository;

import com.kiranapilot.entity.KhataTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface KhataTransactionRepository extends JpaRepository<KhataTransaction, Long> {

    List<KhataTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<KhataTransaction> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime start, OffsetDateTime end);
}
