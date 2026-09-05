package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "khata_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KhataTransaction {

    public enum TransactionType {
        CREDIT_GIVEN,    // Customer bought on credit (Balance increases)
        PAYMENT_RECEIVED, // Customer settled / paid cash/UPI (Balance decreases)
        ADJUSTMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "bill_id", length = 64)
    private String billId;

    @Column(name = "payment_mode", length = 32)
    private String paymentMode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
