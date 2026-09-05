package com.kiranapilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockHealthDto {
    private int totalSkus;
    private int lowStockCount;
    private int outOfStockCount;
    private BigDecimal totalInventoryValuation;
    private List<ProductDto> lowStockProducts;
}
