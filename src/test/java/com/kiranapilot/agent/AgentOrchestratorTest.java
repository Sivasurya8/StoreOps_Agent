package com.kiranapilot.agent;

import com.kiranapilot.agent.tools.*;
import com.kiranapilot.analytics.AnalyticsService;
import com.kiranapilot.billing.BillingService;
import com.kiranapilot.document.PdfInvoiceGenerator;
import com.kiranapilot.document.PptxDeckGenerator;
import com.kiranapilot.dto.DraftBillDto;
import com.kiranapilot.dto.ProductDto;
import com.kiranapilot.dto.SalesSummaryDto;
import com.kiranapilot.dto.StockHealthDto;
import com.kiranapilot.entity.Bill;
import com.kiranapilot.inventory.InventoryService;
import com.kiranapilot.khata.KhataService;
import com.kiranapilot.memory.OwnerPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiranapilot.agent.llm.MockLLMClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private BillingService billingService;

    @Mock
    private KhataService khataService;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private PdfInvoiceGenerator pdfInvoiceGenerator;

    @Mock
    private PptxDeckGenerator pptxDeckGenerator;

    @Mock
    private OwnerPreferenceService preferenceService;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        InventoryTools inventoryTools = new InventoryTools(inventoryService);
        BillingTools billingTools = new BillingTools(billingService);
        KhataTools khataTools = new KhataTools(khataService);
        AnalyticsTools analyticsTools = new AnalyticsTools(analyticsService, inventoryService);
        DocumentTools documentTools = new DocumentTools(billingService, pdfInvoiceGenerator, pptxDeckGenerator, analyticsService, inventoryService);
        PreferenceTools preferenceTools = new PreferenceTools(preferenceService);

        AgentToolRegistry registry = new AgentToolRegistry(
                inventoryTools, billingTools, khataTools, analyticsTools, documentTools, preferenceTools
        );

        MockLLMClient mockLLM = new MockLLMClient();
        orchestrator = new AgentOrchestrator(registry, mockLLM, preferenceService, objectMapper);

        when(preferenceService.getStoreName()).thenReturn("Sri Lakshmi Supermarket");
        when(preferenceService.getStoreGstin()).thenReturn("29AAAPL1234C1ZV");
        when(preferenceService.getDefaultPaymentMode()).thenReturn("UPI");
        when(preferenceService.getAllPreferences()).thenReturn(Collections.emptyMap());
    }

    @Test
    @DisplayName("Scenario 1: Receive stock — '50 packets of Maggi came in, cost 12, MRP 14'")
    void testReceiveStockScenario() {
        when(inventoryService.receiveStock(eq("maggi"), eq(new BigDecimal("50")), eq(new BigDecimal("12")), eq(new BigDecimal("14")), any()))
                .thenReturn(ProductDto.builder().name("Maggi 70g").unit("packet").quantity(new BigDecimal("56")).build());

        AgentOrchestrator.AgentExecutionResult result = orchestrator.processMessage(1L, "50 packets of Maggi came in, cost 12, MRP 14");
        assertNotNull(result.getResponseText());
        assertTrue(result.getResponseText().contains("Successfully received 50 packet of Maggi 70g"));
    }

    @Test
    @DisplayName("Scenario 2: Multi-turn billing with edit — 'make a bill 2kg sugar, 4 Maggi, UPI' -> 'drop the sugar, make it 6 Maggi'")
    void testBillingWithEditScenario() {
        when(billingService.addItem(eq(2L), anyString(), any(BigDecimal.class)))
                .thenReturn(DraftBillDto.builder().chatId(2L).grandTotal(new BigDecimal("140.00")).items(new ArrayList<>()).build());
        when(billingService.setPaymentMode(eq(2L), eq("UPI"), any()))
                .thenReturn(DraftBillDto.builder().chatId(2L).paymentMode("UPI").grandTotal(new BigDecimal("140.00")).items(new ArrayList<>()).build());

        // Turn 1: Build draft
        AgentOrchestrator.AgentExecutionResult turn1 = orchestrator.processMessage(2L, "make a bill: 2kg sugar, 4 Maggi, UPI");
        assertNotNull(turn1.getResponseText());

        // Turn 2: Edit draft
        when(billingService.removeItem(eq(2L), eq("sugar")))
                .thenReturn(DraftBillDto.builder().chatId(2L).grandTotal(new BigDecimal("56.00")).items(new ArrayList<>()).build());
        when(billingService.updateItemQuantity(eq(2L), eq("maggi"), eq(new BigDecimal("6"))))
                .thenReturn(DraftBillDto.builder().chatId(2L).grandTotal(new BigDecimal("84.00")).items(new ArrayList<>()).build());

        AgentOrchestrator.AgentExecutionResult turn2 = orchestrator.processMessage(2L, "drop the sugar, make it 6 Maggi");
        assertNotNull(turn2.getResponseText());
    }

    @Test
    @DisplayName("Scenario 3: Khata cycle — 'put 500 on Ramesh's credit' -> 'Ramesh paid 300'")
    void testKhataScenario() {
        when(khataService.addCredit(eq("Ramesh"), eq(new BigDecimal("500")), any(), any()))
                .thenReturn(com.kiranapilot.dto.KhataSummaryDto.builder().customerName("Ramesh Kumar").currentBalance(new BigDecimal("500.00")).build());

        AgentOrchestrator.AgentExecutionResult creditResult = orchestrator.processMessage(3L, "put 500 on Ramesh's credit");
        assertTrue(creditResult.getResponseText().contains("Added ₹500 to Ramesh Kumar's credit ledger"));

        when(khataService.recordPayment(eq("Ramesh"), eq(new BigDecimal("300")), eq("CASH"), any()))
                .thenReturn(com.kiranapilot.dto.KhataSummaryDto.builder().customerName("Ramesh Kumar").currentBalance(new BigDecimal("200.00")).build());

        AgentOrchestrator.AgentExecutionResult payResult = orchestrator.processMessage(3L, "Ramesh paid 300");
        assertTrue(payResult.getResponseText().contains("Recorded payment of ₹300"));
        assertTrue(payResult.getResponseText().contains("Remaining credit balance: ₹200.00"));
    }

    @Test
    @DisplayName("Scenario 4: Daily close — 'today's sales?'")
    void testDailySalesScenario() {
        when(analyticsService.getDailySalesSummary(any(LocalDate.class)))
                .thenReturn(SalesSummaryDto.builder()
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
                        .totalBillsCount(15)
                        .build());

        AgentOrchestrator.AgentExecutionResult salesResult = orchestrator.processMessage(4L, "today's sales?");
        assertTrue(salesResult.getResponseText().contains("Total Gross Sales: ₹18450.00"));
        assertTrue(salesResult.getResponseText().contains("UPI: ₹10950.00"));
    }

    @Test
    @DisplayName("Scenario 5: Generate PDF Invoice & PPTX Deck artifacts")
    void testArtifactGenerationScenario() {
        Bill bill = Bill.builder().billNumber("KP-20260905-0010").grandTotal(new BigDecimal("500.00")).build();
        when(billingService.getLatestBill(5L)).thenReturn(Optional.of(bill));
        when(pdfInvoiceGenerator.generateInvoicePdf(bill)).thenReturn("%PDF-Sample".getBytes());

        AgentOrchestrator.AgentExecutionResult pdfResult = orchestrator.processMessage(5L, "send me that bill as a PDF");
        assertNotNull(pdfResult.getDocumentAttachment());
        assertEquals("Invoice_KP-20260905-0010.pdf", pdfResult.getAttachmentFilename());
        assertEquals("application/pdf", pdfResult.getAttachmentMimeType());

        when(analyticsService.getPeriodSummary(any(), any())).thenReturn(SalesSummaryDto.builder().totalGrossSales(new BigDecimal("1000")).build());
        when(inventoryService.getStockHealth()).thenReturn(StockHealthDto.builder().totalSkus(10).build());
        when(pptxDeckGenerator.generateDeck(any(), any())).thenReturn("PK-SamplePresentation".getBytes());

        AgentOrchestrator.AgentExecutionResult pptxResult = orchestrator.processMessage(5L, "make this week's sales analysis deck");
        assertNotNull(pptxResult.getDocumentAttachment());
        assertEquals("KiranaPilot_Sales_Analysis_Deck.pptx", pptxResult.getAttachmentFilename());
    }

    @Test
    @DisplayName("Scenario 6: Set standing preference & verify persistence")
    void testPreferenceScenario() {
        AgentOrchestrator.AgentExecutionResult prefResult = orchestrator.processMessage(6L, "always assume UPI unless I say cash");
        verify(preferenceService, times(1)).setPreference("default_payment_mode", "UPI");
        assertTrue(prefResult.getResponseText().contains("Saved preference"));
    }
}
