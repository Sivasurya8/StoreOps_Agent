package com.kiranapilot.repository;

import com.kiranapilot.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByBillNumber(String billNumber);

    List<Bill> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT b FROM Bill b WHERE b.status = 'COMPLETED' AND b.createdAt >= :since ORDER BY b.createdAt DESC")
    List<Bill> findCompletedBillsSince(@Param("since") OffsetDateTime since);

    @Query("SELECT b FROM Bill b WHERE b.status = 'COMPLETED' AND b.createdAt BETWEEN :start AND :end ORDER BY b.createdAt DESC")
    List<Bill> findCompletedBillsBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    Optional<Bill> findTopByChatIdOrderByCreatedAtDesc(Long chatId);
}
