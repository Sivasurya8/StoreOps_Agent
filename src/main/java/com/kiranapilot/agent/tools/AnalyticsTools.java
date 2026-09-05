package com.kiranapilot.agent.tools;

import com.kiranapilot.agent.ToolResult;
import com.kiranapilot.analytics.AnalyticsService;
import com.kiranapilot.dto.GstBreakupDto;
import com.kiranapilot.dto.SalesSummaryDto;
import com.kiranapilot.dto.StockHealthDto;
import com.kiranapilot.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsTools {

    private final AnalyticsService analyticsService;
    private final InventoryService inventoryService;

    /**
     * Tool: get_daily_sales_summary
     * Returns total sales, tax collected, cash vs UPI split, and top items ("today's sales?", "close the day").
     */
    public ToolResult getDailySalesSummary(Map<String, Object> args) {
        try {
            LocalDate date = args.get("date") != null ? LocalDate.parse((String) args.get("date")) : LocalDate.now();
            SalesSummaryDto summary = analyticsService.getDailySalesSummary(date);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📊 Store Sales Summary (%s):\n\n", date));
            sb.append(String.format("• Total Gross Sales: ₹%s (%d bills)\n", summary.getTotalGrossSales(), summary.getTotalBillsCount()));
            sb.append(String.format("• Taxable Turnover: ₹%s\n", summary.getTotalTaxableSales()));
            sb.append(String.format("• GST Collected: ₹%s (CGST ₹%s + SGST ₹%s)\n", summary.getTotalGstCollected(), summary.getTotalCgst(), summary.getTotalSgst()));
            sb.append(String.format("• Estimated Net Margin/Profit: ₹%s\n\n", summary.getTotalEstimatedProfit()));

            sb.append("💳 Payment Mode Breakdown:\n");
            sb.append(String.format("• UPI: ₹%s\n", summary.getUpiSales()));
            sb.append(String.format("• Cash: ₹%s\n", summary.getCashSales()));
            sb.append(String.format("• Card: ₹%s\n", summary.getCardSales()));
            sb.append(String.format("• Khata Credit: ₹%s\n\n", summary.getKhataSales()));

            if (summary.getTopSellingProducts() != null && !summary.getTopSellingProducts().isEmpty()) {
                sb.append("🏆 Top Selling Items Today:\n");
                int rank = 1;
                for (SalesSummaryDto.TopProductItemDto item : summary.getTopSellingProducts()) {
                    sb.append(String.format("%d. %s — %s units (₹%s)\n",
                            rank++, item.getProductName(), item.getQuantity().stripTrailingZeros().toPlainString(), item.getTotalRevenue()));
                }
                sb.append("\n");
            }

            if (summary.getTaxBreakup() != null && !summary.getTaxBreakup().isEmpty()) {
                sb.append("🏛 GST Slabs:\n");
                for (GstBreakupDto slab : summary.getTaxBreakup()) {
                    sb.append(String.format("• %s%% GST: Taxable ₹%s, Tax ₹%s\n",
                            slab.getGstRate().stripTrailingZeros().toPlainString(), slab.getTaxableAmount(), slab.getTotalTax()));
                }
            }

            return ToolResult.success(summary, sb.toString());
        } catch (Exception e) {
            log.error("Error generating sales summary", e);
            return ToolResult.error("Failed to calculate sales summary: " + e.getMessage());
        }
    }

    /**
     * Tool: get_stock_health
     * Returns total catalog valuation, low stock alerts, and out-of-stock items.
     */
    public ToolResult getStockHealth(Map<String, Object> args) {
        try {
            StockHealthDto health = inventoryService.getStockHealth();
            StringBuilder sb = new StringBuilder();
            sb.append("📦 Inventory Health Report:\n");
            sb.append(String.format("• Total SKUs: %d\n", health.getTotalSkus()));
            sb.append(String.format("• Total Inventory Valuation: ₹%s\n", health.getTotalInventoryValuation()));
            sb.append(String.format("• Low Stock Alert Count: %d\n", health.getLowStockCount()));
            sb.append(String.format("• Out of Stock Count: %d\n", health.getOutOfStockCount()));
            return ToolResult.success(health, sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Failed to check inventory health: " + e.getMessage());
        }
    }
}
