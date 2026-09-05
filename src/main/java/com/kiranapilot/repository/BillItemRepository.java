package com.kiranapilot.repository;

import com.kiranapilot.entity.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BillItemRepository extends JpaRepository<BillItem, Long> {

    List<BillItem> findByBillId(Long billId);

    @Query("SELECT bi.productName, SUM(bi.quantity), SUM(bi.totalAmount) " +
           "FROM BillItem bi JOIN bi.bill b " +
           "WHERE b.status = 'COMPLETED' AND b.createdAt BETWEEN :start AND :end " +
           "GROUP BY bi.productName " +
           "ORDER BY SUM(bi.quantity) DESC")
    List<Object[]> findTopSellingProductsBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
