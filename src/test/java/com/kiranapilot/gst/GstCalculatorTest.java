package com.kiranapilot.gst;

import com.kiranapilot.dto.DraftBillItemDto;
import com.kiranapilot.dto.GstBreakupDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GstCalculatorTest {

    private GstCalculator gstCalculator;

    @BeforeEach
    void setUp() {
        gstCalculator = new GstCalculator();
    }

    @Test
    @DisplayName("0% GST: Staples like loose sugar or loose rice carry zero tax")
    void testZeroGstCalculation() {
        DraftBillItemDto item = gstCalculator.calculateLineItem(
                1L, "SKU-SUGAR", "Sugar Loose", "kg",
                new BigDecimal("2.0"), new BigDecimal("42.00"), new BigDecimal("45.00"),
                BigDecimal.ZERO, "1701"
        );

        assertEquals(new BigDecimal("84.00"), item.getTotalAmount());
        assertEquals(new BigDecimal("84.00"), item.getTaxableValue());
        assertEquals(new BigDecimal("0.00"), item.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), item.getSgstAmount());
    }

    @Test
    @DisplayName("5% GST: Packaged staples like Aashirvaad Atta 5kg (5% = 2.5% CGST + 2.5% SGST)")
    void testFivePercentGstCalculation() {
        DraftBillItemDto item = gstCalculator.calculateLineItem(
                2L, "SKU-ATTA-5KG", "Aashirvaad Atta 5kg", "packet",
                new BigDecimal("1.0"), new BigDecimal("275.00"), new BigDecimal("290.00"),
                new BigDecimal("5.00"), "1101"
        );

        assertEquals(new BigDecimal("275.00"), item.getTotalAmount());
        // Taxable = 275 / 1.05 = 261.90
        assertEquals(new BigDecimal("261.90"), item.getTaxableValue());
        // Total Tax = 13.10 -> CGST: 6.55, SGST: 6.55
        assertEquals(new BigDecimal("6.55"), item.getCgstAmount());
        assertEquals(new BigDecimal("6.55"), item.getSgstAmount());

        // Exact match check: Taxable + CGST + SGST == Total
        assertEquals(item.getTotalAmount(), item.getTaxableValue().add(item.getCgstAmount()).add(item.getSgstAmount()));
    }

    @Test
    @DisplayName("12% GST: FMCG items like Maggi noodles (12% = 6% CGST + 6% SGST)")
    void testTwelvePercentGstCalculation() {
        DraftBillItemDto item = gstCalculator.calculateLineItem(
                3L, "SKU-MAGGI", "Maggi 70g", "packet",
                new BigDecimal("4.0"), new BigDecimal("14.00"), new BigDecimal("14.00"),
                new BigDecimal("12.00"), "1902"
        );

        assertEquals(new BigDecimal("56.00"), item.getTotalAmount());
        // Taxable = 56 / 1.12 = 50.00
        assertEquals(new BigDecimal("50.00"), item.getTaxableValue());
        // Total Tax = 6.00 -> CGST: 3.00, SGST: 3.00
        assertEquals(new BigDecimal("3.00"), item.getCgstAmount());
        assertEquals(new BigDecimal("3.00"), item.getSgstAmount());
    }

    @Test
    @DisplayName("18% GST: Detergent like Surf Excel (18% = 9% CGST + 9% SGST)")
    void testEighteenPercentGstCalculation() {
        DraftBillItemDto item = gstCalculator.calculateLineItem(
                4L, "SKU-SURF", "Surf Excel 1kg", "packet",
                new BigDecimal("1.0"), new BigDecimal("138.00"), new BigDecimal("145.00"),
                new BigDecimal("18.00"), "3402"
        );

        assertEquals(new BigDecimal("138.00"), item.getTotalAmount());
        assertEquals(new BigDecimal("116.95"), item.getTaxableValue());
        assertEquals(new BigDecimal("10.53"), item.getCgstAmount());
        assertEquals(new BigDecimal("10.52"), item.getSgstAmount());

        // Exact match check
        assertEquals(item.getTotalAmount(), item.getTaxableValue().add(item.getCgstAmount()).add(item.getSgstAmount()));
    }

    @Test
    @DisplayName("Aggregate Tax Breakup by Slabs")
    void testTaxBreakupAggregation() {
        DraftBillItemDto item1 = gstCalculator.calculateLineItem(1L, "SKU-1", "Item 0%", "kg", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, "1000");
        DraftBillItemDto item2 = gstCalculator.calculateLineItem(2L, "SKU-2", "Item 5%", "pkt", BigDecimal.ONE, new BigDecimal("105.00"), new BigDecimal("105.00"), new BigDecimal("5.00"), "2000");
        DraftBillItemDto item3 = gstCalculator.calculateLineItem(3L, "SKU-3", "Item 12%", "pkt", BigDecimal.ONE, new BigDecimal("112.00"), new BigDecimal("112.00"), new BigDecimal("12.00"), "3000");

        List<GstBreakupDto> breakup = gstCalculator.computeTaxBreakup(List.of(item1, item2, item3));

        assertEquals(3, breakup.size());
        assertEquals(new BigDecimal("0.00"), breakup.get(0).getGstRate());
        assertEquals(new BigDecimal("5.00"), breakup.get(1).getGstRate());
        assertEquals(new BigDecimal("12.00"), breakup.get(2).getGstRate());
    }
}
