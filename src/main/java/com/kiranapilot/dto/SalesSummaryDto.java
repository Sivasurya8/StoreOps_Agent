package com.kiranapilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesSummaryDto {
    private LocalDate reportDate;
    private int totalBillsCount;
    private BigDecimal totalGrossSales;
    private BigDecimal totalTaxableSales;
    private BigDecimal totalGstCollected;
    private BigDecimal totalCgst;
    private BigDecimal totalSgst;
    private BigDecimal totalEstimatedProfit;

    private BigDecimal cashSales;
    private BigDecimal upiSales;
    private BigDecimal cardSales;
    private BigDecimal khataSales;

    private List<TopProductItemDto> topSellingProducts;
    private List<GstBreakupDto> taxBreakup;
    private int lowStockItemsCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopProductItemDto {
        private String productName;
        private BigDecimal quantity;
        private BigDecimal totalRevenue;
    }
}
