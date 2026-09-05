package com.kiranapilot.agent.tools;

import com.kiranapilot.agent.ToolResult;
import com.kiranapilot.analytics.AnalyticsService;
import com.kiranapilot.billing.BillingService;
import com.kiranapilot.document.PdfInvoiceGenerator;
import com.kiranapilot.document.PptxDeckGenerator;
import com.kiranapilot.dto.SalesSummaryDto;
import com.kiranapilot.dto.StockHealthDto;
import com.kiranapilot.entity.Bill;
import com.kiranapilot.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentTools {

    private final BillingService billingService;
    private final PdfInvoiceGenerator pdfInvoiceGenerator;
    private final PptxDeckGenerator pptxDeckGenerator;
    private final AnalyticsService analyticsService;
    private final InventoryService inventoryService;

    /**
     * Tool: generate_invoice_pdf
     * Produces a clean, GST-compliant PDF invoice document for the latest or specified bill.
     */
    public ToolResult generateInvoicePdf(Map<String, Object> args, Long chatId) {
        try {
            String billNumber = (String) args.get("bill_number");
            Optional<Bill> optBill;

            if (billNumber != null && !billNumber.trim().isEmpty()) {
                optBill = billingService.getBillByNumber(billNumber.trim());
            } else {
                optBill = billingService.getLatestBill(chatId);
            }

            if (optBill.isEmpty()) {
                return ToolResult.error("No recent finalized bill found to generate invoice PDF. Please finalize a bill first.");
            }

            Bill bill = optBill.get();
            byte[] pdfBytes = pdfInvoiceGenerator.generateInvoicePdf(bill);
            String filename = "Invoice_" + bill.getBillNumber() + ".pdf";

            return ToolResult.withAttachment(
                    bill,
                    "Generated GST Tax Invoice PDF for Bill #" + bill.getBillNumber(),
                    pdfBytes,
                    filename,
                    "application/pdf"
            );
        } catch (Exception e) {
            log.error("Failed to generate PDF invoice", e);
            return ToolResult.error("Failed to generate PDF invoice: " + e.getMessage());
        }
    }

    /**
     * Tool: generate_analysis_deck
     * Generates a PowerPoint presentation deck (.pptx) with performance metrics and charts.
     */
    public ToolResult generateAnalysisDeck(Map<String, Object> args) {
        try {
            LocalDate start = LocalDate.now().minusDays(7);
            LocalDate end = LocalDate.now();

            SalesSummaryDto sales = analyticsService.getPeriodSummary(start, end);
            StockHealthDto stockHealth = inventoryService.getStockHealth();

            byte[] pptxBytes = pptxDeckGenerator.generateDeck(sales, stockHealth);
            String filename = "KiranaPilot_Sales_Analysis_Deck.pptx";

            return ToolResult.withAttachment(
                    sales,
                    "Generated Weekly Sales & Inventory Analysis Presentation Deck (PPTX).",
                    pptxBytes,
                    filename,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            );
        } catch (Exception e) {
            log.error("Failed to generate PPTX deck", e);
            return ToolResult.error("Failed to generate analysis deck: " + e.getMessage());
        }
    }
}
