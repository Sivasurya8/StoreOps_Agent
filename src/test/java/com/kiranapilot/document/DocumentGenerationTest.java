package com.kiranapilot.document;

import com.kiranapilot.dto.GstBreakupDto;
import com.kiranapilot.dto.SalesSummaryDto;
import com.kiranapilot.dto.StockHealthDto;
import com.kiranapilot.entity.Bill;
import com.kiranapilot.entity.BillItem;
import com.kiranapilot.gst.GstCalculator;
import com.kiranapilot.memory.OwnerPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationTest {

    @Mock
    private OwnerPreferenceService preferenceService;

    private PdfInvoiceGenerator pdfInvoiceGenerator;
    private PptxDeckGenerator pptxDeckGenerator;

    @BeforeEach
    void setUp() {
        pdfInvoiceGenerator = new PdfInvoiceGenerator(preferenceService, new GstCalculator());
        pptxDeckGenerator = new PptxDeckGenerator(preferenceService);
    }

    @Test
    @DisplayName("PDF Invoice: Generates clean, well-formatted GST Invoice PDF")
    void testPdfInvoiceGeneration() {
        when(preferenceService.getStoreName()).thenReturn("Sri Lakshmi Supermarket");
        when(preferenceService.getStoreAddress()).thenReturn("124 Main Bazaar, Bangalore - 560001");
        when(preferenceService.getStoreGstin()).thenReturn("29AAAPL1234C1ZV");
        when(preferenceService.getStorePhone()).thenReturn("+91 98765 43210");

        Bill bill = Bill.builder()
                .billNumber("KP-20260905-0001")
                .totalTaxable(new BigDecimal("300.00"))
                .totalCgst(new BigDecimal("15.00"))
                .totalSgst(new BigDecimal("15.00"))
                .totalTax(new BigDecimal("30.00"))
                .grandTotal(new BigDecimal("330.00"))
                .paymentMode(Bill.PaymentMode.UPI)
                .paymentReference("UPI/654321")
                .createdAt(OffsetDateTime.now())
                .build();

        BillItem item1 = BillItem.builder()
                .productName("Aashirvaad Atta 5kg")
                .hsnCode("1101")
                .quantity(new BigDecimal("1"))
                .unit("packet")
                .unitPrice(new BigDecimal("275.00"))
                .mrp(new BigDecimal("290.00"))
                .gstRate(new BigDecimal("5.00"))
                .taxableValue(new BigDecimal("261.90"))
                .cgstAmount(new BigDecimal("6.55"))
                .sgstAmount(new BigDecimal("6.55"))
                .totalAmount(new BigDecimal("275.00"))
                .build();
        bill.addItem(item1);

        byte[] pdfBytes = pdfInvoiceGenerator.generateInvoicePdf(bill);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF should contain valid byte stream");
        // Verify PDF Magic Bytes (%PDF)
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    @DisplayName("PPTX Deck: Generates PowerPoint analysis deck with charts and insights")
    void testPptxDeckGeneration() {
        when(preferenceService.getStoreName()).thenReturn("Sri Lakshmi Supermarket");

        SalesSummaryDto sales = SalesSummaryDto.builder()
                .reportDate(LocalDate.now())
                .totalBillsCount(24)
                .totalGrossSales(new BigDecimal("18450.00"))
                .totalTaxableSales(new BigDecimal("16500.00"))
                .totalGstCollected(new BigDecimal("1950.00"))
                .totalCgst(new BigDecimal("975.00"))
                .totalSgst(new BigDecimal("975.00"))
                .totalEstimatedProfit(new BigDecimal("3450.00"))
                .cashSales(new BigDecimal("6500.00"))
                .upiSales(new BigDecimal("10950.00"))
                .cardSales(new BigDecimal("1000.00"))
                .khataSales(BigDecimal.ZERO)
                .topSellingProducts(List.of(
                        new SalesSummaryDto.TopProductItemDto("Maggi 70g", new BigDecimal("40"), new BigDecimal("560.00")),
                        new SalesSummaryDto.TopProductItemDto("Aashirvaad Atta 5kg", new BigDecimal("12"), new BigDecimal("3300.00"))
                ))
                .taxBreakup(List.of(
                        new GstBreakupDto(new BigDecimal("5.00"), new BigDecimal("5000.00"), new BigDecimal("125.00"), new BigDecimal("125.00"), new BigDecimal("250.00")),
                        new GstBreakupDto(new BigDecimal("12.00"), new BigDecimal("8000.00"), new BigDecimal("480.00"), new BigDecimal("480.00"), new BigDecimal("960.00"))
                ))
                .lowStockItemsCount(3)
                .build();

        StockHealthDto stockHealth = StockHealthDto.builder()
                .totalSkus(18)
                .lowStockCount(3)
                .outOfStockCount(0)
                .totalInventoryValuation(new BigDecimal("45000.00"))
                .lowStockProducts(Collections.emptyList())
                .build();

        byte[] pptxBytes = pptxDeckGenerator.generateDeck(sales, stockHealth);

        assertNotNull(pptxBytes);
        assertTrue(pptxBytes.length > 1000, "PPTX should contain valid presentation byte stream");
        // Verify ZIP Magic Bytes (PK..)
        assertEquals('P', (char) pptxBytes[0]);
        assertEquals('K', (char) pptxBytes[1]);
    }
}
