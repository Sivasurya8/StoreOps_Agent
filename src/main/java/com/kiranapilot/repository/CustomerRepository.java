package com.kiranapilot.repository;

import com.kiranapilot.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByNameIgnoreCase(String name);

    @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.normalizedName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "c.phone LIKE CONCAT('%', :query, '%')")
    List<Customer> searchCustomers(@Param("query") String query);

    @Modifying
    @Query("UPDATE Customer c SET c.currentCreditBalance = c.currentCreditBalance + :delta WHERE c.id = :id")
    int updateBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
