package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 32)
    private String unit; // kg, g, litre, ml, packet, dozen, piece

    @Column(name = "is_packaged", nullable = false)
    @Builder.Default
    private Boolean isPackaged = true;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal reorderLevel = new BigDecimal("5.000");

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "hsn_code", nullable = false, length = 32)
    private String hsnCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
