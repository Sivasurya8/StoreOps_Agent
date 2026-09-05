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
public class ProductDto {
    private Long id;
    private String sku;
    private String name;
    private String unit;
    private Boolean isPackaged;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal mrp;
    private BigDecimal quantity;
    private BigDecimal reorderLevel;
    private BigDecimal gstRate;
    private String hsnCode;
    private Boolean active;
}
