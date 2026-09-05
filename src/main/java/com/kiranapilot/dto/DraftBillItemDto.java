package com.kiranapilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftBillItemDto {
    private Long productId;
    private String sku;
    private String productName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal mrp;
    private BigDecimal gstRate;
    private String hsnCode;

    private BigDecimal taxableValue;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal totalAmount;
}
