package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Column(name = "hsn_code", nullable = false, length = 32)
    private String hsnCode;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Column(name = "cgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
}
