package com.kiranapilot.repository;

import com.kiranapilot.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByNameIgnoreCase(String name);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.normalizedName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchActiveProducts(@Param("query") String query);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.quantity <= p.reorderLevel ORDER BY p.quantity ASC")
    List<Product> findLowStockProducts();

    /**
     * Atomic oversell guard: Decrements quantity ONLY if current quantity is >= requested quantity.
     * Returns 1 if successful, 0 if insufficient stock.
     */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :qty WHERE p.id = :id AND p.quantity >= :qty")
    int decrementStockAtomic(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /**
     * Atomic increment for stock receipt or cancellation rollbacks.
     */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity + :qty WHERE p.id = :id")
    int incrementStockAtomic(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
