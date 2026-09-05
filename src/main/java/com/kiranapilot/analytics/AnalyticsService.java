package com.kiranapilot.analytics;

import com.kiranapilot.dto.GstBreakupDto;
import com.kiranapilot.dto.SalesSummaryDto;
import com.kiranapilot.entity.Bill;
import com.kiranapilot.entity.BillItem;
import com.kiranapilot.entity.Product;
import com.kiranapilot.inventory.InventoryService;
import com.kiranapilot.repository.BillItemRepository;
import com.kiranapilot.repository.BillRepository;
import com.kiranapilot.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public SalesSummaryDto getDailySalesSummary(LocalDate date) {
        if (date == null) date = LocalDate.now();
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = date.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        return generateSummaryForPeriod(date, start, end);
    }

    @Transactional(readOnly = true)
    public SalesSummaryDto getPeriodSummary(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(7);
        if (endDate == null) endDate = LocalDate.now();
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        return generateSummaryForPeriod(startDate, start, end);
    }

    private SalesSummaryDto generateSummaryForPeriod(LocalDate refDate, OffsetDateTime start, OffsetDateTime end) {
        List<Bill> bills = billRepository.findCompletedBillsBetween(start, end);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal taxableSales = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal upiSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal khataSales = BigDecimal.ZERO;

        Map<BigDecimal, BigDecimal[]> slabMap = new HashMap<>(); // slab -> [taxable, tax]

        for (Bill bill : bills) {
            grossSales = grossSales.add(bill.getGrandTotal());
            taxableSales = taxableSales.add(bill.getTotalTaxable());
            totalCgst = totalCgst.add(bill.getTotalCgst());
            totalSgst = totalSgst.add(bill.getTotalSgst());
            totalTax = totalTax.add(bill.getTotalTax());

            if (bill.getPaymentMode() == Bill.PaymentMode.CASH) {
                cashSales = cashSales.add(bill.getGrandTotal());
            } else if (bill.getPaymentMode() == Bill.PaymentMode.UPI) {
                upiSales = upiSales.add(bill.getGrandTotal());
            } else if (bill.getPaymentMode() == Bill.PaymentMode.CARD) {
                cardSales = cardSales.add(bill.getGrandTotal());
            } else if (bill.getPaymentMode() == Bill.PaymentMode.KHATA) {
                khataSales = khataSales.add(bill.getGrandTotal());
            }

            for (BillItem item : bill.getItems()) {
                BigDecimal slab = item.getGstRate().setScale(2, RoundingMode.HALF_UP);
                BigDecimal[] curr = slabMap.computeIfAbsent(slab, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                curr[0] = curr[0].add(item.getTaxableValue());
                curr[1] = curr[1].add(item.getCgstAmount().add(item.getSgstAmount()));

                // Profit = (sellingPrice - costPrice) * qty
                if (item.getProduct() != null && item.getProduct().getCostPrice() != null) {
                    BigDecimal margin = item.getUnitPrice().subtract(item.getProduct().getCostPrice());
                    BigDecimal itemProfit = margin.multiply(item.getQuantity());
                    totalProfit = totalProfit.add(itemProfit);
                }
            }
        }

        List<GstBreakupDto> taxBreakup = slabMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    BigDecimal cgst = e.getValue()[1].divide(new BigDecimal("2.00"), 2, RoundingMode.HALF_UP);
                    BigDecimal sgst = e.getValue()[1].subtract(cgst);
                    return GstBreakupDto.builder()
                            .gstRate(e.getKey())
                            .taxableAmount(e.getValue()[0])
                            .cgstAmount(cgst)
                            .sgstAmount(sgst)
                            .totalTax(e.getValue()[1])
                            .build();
                })
                .collect(Collectors.toList());

        List<Object[]> topRows = billItemRepository.findTopSellingProductsBetween(start, end);
        List<SalesSummaryDto.TopProductItemDto> topItems = new ArrayList<>();
        for (Object[] row : topRows) {
            String pName = (String) row[0];
            BigDecimal qty = (BigDecimal) row[1];
            BigDecimal rev = (BigDecimal) row[2];
            topItems.add(SalesSummaryDto.TopProductItemDto.builder()
                    .productName(pName)
                    .quantity(qty)
                    .totalRevenue(rev)
                    .build());
            if (topItems.size() >= 5) break;
        }

        int lowStockCount = inventoryService.getLowStockProducts().size();

        return SalesSummaryDto.builder()
                .reportDate(refDate)
                .totalBillsCount(bills.size())
                .totalGrossSales(grossSales.setScale(2, RoundingMode.HALF_UP))
                .totalTaxableSales(taxableSales.setScale(2, RoundingMode.HALF_UP))
                .totalGstCollected(totalTax.setScale(2, RoundingMode.HALF_UP))
                .totalCgst(totalCgst.setScale(2, RoundingMode.HALF_UP))
                .totalSgst(totalSgst.setScale(2, RoundingMode.HALF_UP))
                .totalEstimatedProfit(totalProfit.setScale(2, RoundingMode.HALF_UP))
                .cashSales(cashSales.setScale(2, RoundingMode.HALF_UP))
                .upiSales(upiSales.setScale(2, RoundingMode.HALF_UP))
                .cardSales(cardSales.setScale(2, RoundingMode.HALF_UP))
                .khataSales(khataSales.setScale(2, RoundingMode.HALF_UP))
                .topSellingProducts(topItems)
                .taxBreakup(taxBreakup)
                .lowStockItemsCount(lowStockCount)
                .build();
    }
}
