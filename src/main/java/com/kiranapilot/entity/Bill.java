package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bill {

    public enum BillStatus {
        COMPLETED,
        CANCELLED
    }

    public enum PaymentMode {
        CASH,
        UPI,
        CARD,
        KHATA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_number", nullable = false, unique = true, length = 64)
    private String billNumber;

    @Column(name = "chat_id")
    private Long chatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "total_taxable", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTaxable;

    @Column(name = "total_cgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCgst;

    @Column(name = "total_sgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSgst;

    @Column(name = "total_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTax;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 32)
    private PaymentMode paymentMode;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private BillStatus status = BillStatus.COMPLETED;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BillItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public void addItem(BillItem item) {
        items.add(item);
        item.setBill(this);
    }
}
