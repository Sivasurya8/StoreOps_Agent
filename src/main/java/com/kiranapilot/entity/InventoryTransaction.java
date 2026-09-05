package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    public enum TransactionType {
        STOCK_IN,
        SALE,
        RETURN,
        ADJUSTMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private TransactionType transactionType;

    @Column(name = "quantity_change", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityChange;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
