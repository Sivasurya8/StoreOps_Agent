package com.kiranapilot.repository;

import com.kiranapilot.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<InventoryTransaction> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime start, OffsetDateTime end);
}
