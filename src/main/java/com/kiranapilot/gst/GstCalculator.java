package com.kiranapilot.gst;

import com.kiranapilot.dto.DraftBillItemDto;
import com.kiranapilot.dto.GstBreakupDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GstCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TWO = new BigDecimal("2.00");

    /**
     * Calculates line item tax details where sellingPrice is inclusive of GST (standard retail in India).
     * Taxable Value = Total Amount / (1 + (gstRate / 100))
     * Total Tax = Total Amount - Taxable Value
     * CGST = Total Tax / 2
     * SGST = Total Tax - CGST (preserves exact match)
     */
    public DraftBillItemDto calculateLineItem(
            Long productId,
            String sku,
            String productName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitSellingPrice,
            BigDecimal mrp,
            BigDecimal gstRate,
            String hsnCode
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = BigDecimal.ONE;
        }
        if (gstRate == null) {
            gstRate = BigDecimal.ZERO;
        }

        // Line gross total = unitSellingPrice * quantity
        BigDecimal totalAmount = unitSellingPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxableValue;
        BigDecimal totalTax;
        BigDecimal cgstAmount;
        BigDecimal sgstAmount;

        if (gstRate.compareTo(BigDecimal.ZERO) == 0) {
            taxableValue = totalAmount;
            totalTax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            cgstAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            sgstAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            // Factor = 1 + (gstRate / 100) = (100 + gstRate) / 100
            BigDecimal factor = HUNDRED.add(gstRate).divide(HUNDRED, 6, RoundingMode.HALF_UP);
            taxableValue = totalAmount.divide(factor, 2, RoundingMode.HALF_UP);
            totalTax = totalAmount.subtract(taxableValue).setScale(2, RoundingMode.HALF_UP);

            // 50-50 split for Intra-state CGST & SGST
            cgstAmount = totalTax.divide(TWO, 2, RoundingMode.HALF_UP);
            sgstAmount = totalTax.subtract(cgstAmount); // Avoid 1-paise split asymmetry
        }

        return DraftBillItemDto.builder()
                .productId(productId)
                .sku(sku)
                .productName(productName)
                .unit(unit)
                .quantity(quantity)
                .unitPrice(unitSellingPrice)
                .mrp(mrp)
                .gstRate(gstRate.setScale(2, RoundingMode.HALF_UP))
                .hsnCode(hsnCode != null ? hsnCode : "N/A")
                .taxableValue(taxableValue)
                .cgstAmount(cgstAmount)
                .sgstAmount(sgstAmount)
                .totalAmount(totalAmount)
                .build();
    }

    /**
     * Aggregates tax breakup grouped by GST slab (0%, 5%, 12%, 18%, 28%).
     */
    public List<GstBreakupDto> computeTaxBreakup(List<DraftBillItemDto> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        Map<BigDecimal, List<DraftBillItemDto>> grouped = items.stream()
                .collect(Collectors.groupingBy(item -> item.getGstRate().setScale(2, RoundingMode.HALF_UP)));

        List<GstBreakupDto> breakupList = new ArrayList<>();

        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    BigDecimal slabRate = entry.getKey();
                    List<DraftBillItemDto> slabItems = entry.getValue();

                    BigDecimal taxable = slabItems.stream()
                            .map(DraftBillItemDto::getTaxableValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal cgst = slabItems.stream()
                            .map(DraftBillItemDto::getCgstAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal sgst = slabItems.stream()
                            .map(DraftBillItemDto::getSgstAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalTax = cgst.add(sgst);

                    breakupList.add(GstBreakupDto.builder()
                            .gstRate(slabRate)
                            .taxableAmount(taxable)
                            .cgstAmount(cgst)
                            .sgstAmount(sgst)
                            .totalTax(totalTax)
                            .build());
                });

        return breakupList;
    }
}
