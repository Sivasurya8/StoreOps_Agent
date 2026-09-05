package com.kiranapilot.document;

import com.kiranapilot.dto.GstBreakupDto;
import com.kiranapilot.dto.ProductDto;
import com.kiranapilot.dto.SalesSummaryDto;
import com.kiranapilot.dto.StockHealthDto;
import com.kiranapilot.memory.OwnerPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PptxDeckGenerator {

    private final OwnerPreferenceService preferenceService;

    private static final Color PRIMARY_BLUE = new Color(13, 71, 161);
    private static final Color ACCENT_TEAL = new Color(0, 150, 136);
    private static final Color DARK_BG = new Color(24, 32, 47);
    private static final Color CARD_BG = new Color(245, 247, 250);
    private static final Color TEXT_DARK = new Color(33, 37, 41);
    private static final Color TEXT_MUTED = new Color(108, 117, 125);
    private static final Color BORDER_COLOR = new Color(220, 224, 230);

    public byte[] generateDeck(SalesSummaryDto sales, StockHealthDto stockHealth) {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Slide 1: Cover Slide (Dark Theme)
            createCoverSlide(ppt, sales);

            // Slide 2: KPI Executive Dashboard
            createKpiDashboardSlide(ppt, sales);

            // Slide 3: Payment Channel Breakdown
            createPaymentBreakdownSlide(ppt, sales);

            // Slide 4: Top Performing Products
            createTopProductsSlide(ppt, sales);

            // Slide 5: GST Slab Breakdown
            createGstTaxSlabSlide(ppt, sales);

            // Slide 6: Inventory Health & Reorder Radar
            createInventoryHealthSlide(ppt, stockHealth);

            ppt.write(baos);
            log.info("Generated PPTX Business Analysis Deck successfully.");
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PPTX deck", e);
            throw new RuntimeException("PPTX deck generation failed: " + e.getMessage(), e);
        }
    }

    private void createCoverSlide(XMLSlideShow ppt, SalesSummaryDto sales) {
        XSLFSlide slide = ppt.createSlide();

        // Dark Background shape
        XSLFAutoShape bg = slide.createAutoShape();
        bg.setAnchor(new Rectangle(0, 0, 720, 540));
        bg.setFillColor(DARK_BG);

        // Title box
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(50, 160, 620, 180));
        
        XSLFTextParagraph p1 = titleBox.addNewTextParagraph();
        XSLFTextRun r1 = p1.addNewTextRun();
        r1.setText("KiranaPilot Intelligence");
        r1.setFontSize(36.0);
        r1.setBold(true);
        r1.setFontColor(Color.WHITE);

        XSLFTextParagraph p2 = titleBox.addNewTextParagraph();
        XSLFTextRun r2 = p2.addNewTextRun();
        r2.setText("Weekly Store Performance & Sales Analysis Deck");
        r2.setFontSize(20.0);
        r2.setFontColor(new Color(178, 223, 219));

        XSLFTextParagraph p3 = titleBox.addNewTextParagraph();
        p3.setSpaceBefore(25.0);
        XSLFTextRun r3 = p3.addNewTextRun();
        String dateStr = sales.getReportDate() != null ? sales.getReportDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")) : "Current Period";
        r3.setText("Store: " + preferenceService.getStoreName() + " | Date: " + dateStr);
        r3.setFontSize(13.0);
        r3.setFontColor(Color.LIGHT_GRAY);
    }

    private void createKpiDashboardSlide(XMLSlideShow ppt, SalesSummaryDto sales) {
        XSLFSlide slide = ppt.createSlide();
        addSlideHeader(slide, "Executive Summary & Key Metrics", "High-level performance snapshot");

        // 4 KPI Cards
        createKpiCard(slide, 40, 110, 145, 110, "Total Sales", "₹ " + sales.getTotalGrossSales().toPlainString(), PRIMARY_BLUE);
        createKpiCard(slide, 205, 110, 145, 110, "GST Collected", "₹ " + sales.getTotalGstCollected().toPlainString(), ACCENT_TEAL);
        createKpiCard(slide, 370, 110, 145, 110, "Estimated Profit", "₹ " + sales.getTotalEstimatedProfit().toPlainString(), new Color(46, 125, 50));
        createKpiCard(slide, 535, 110, 145, 110, "Total Bills", String.valueOf(sales.getTotalBillsCount()), new Color(230, 81, 0));

        // Insights Box
        XSLFTextBox insights = slide.createTextBox();
        insights.setAnchor(new Rectangle(40, 240, 640, 240));
        insights.setFillColor(CARD_BG);
        insights.setLineColor(BORDER_COLOR);

        XSLFTextParagraph title = insights.addNewTextParagraph();
        XSLFTextRun tr = title.addNewTextRun();
        tr.setText("Key Operational Observations:");
        tr.setBold(true);
        tr.setFontSize(14.0);
        tr.setFontColor(TEXT_DARK);

        addBullet(insights, "Revenue Stability: Generated total gross billing of ₹" + sales.getTotalGrossSales() + " across " + sales.getTotalBillsCount() + " customer transactions.");
        addBullet(insights, "Tax Contribution: Intra-state GST collections total ₹" + sales.getTotalGstCollected() + " (CGST: ₹" + sales.getTotalCgst() + ", SGST: ₹" + sales.getTotalSgst() + ").");
        addBullet(insights, "Gross Margins: Estimated net merchant profit margin of ₹" + sales.getTotalEstimatedProfit() + " based on SKU cost profiles.");
        addBullet(insights, "Low-stock Alert: " + sales.getLowStockItemsCount() + " SKUs currently near or below safety reorder threshold.");
    }

    private void createPaymentBreakdownSlide(XMLSlideShow ppt, SalesSummaryDto sales) {
        XSLFSlide slide = ppt.createSlide();
        addSlideHeader(slide, "Payment Channel Distribution", "Sales volume segregated by settlement channel");

        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle(50, 130, 620, 220));

        String[] headers = {"Payment Mode", "Total Volume (₹)", "Share of Total (%)", "Settlement Velocity"};
        XSLFTableRow hRow = table.addRow();
        for (String h : headers) {
            XSLFTableCell cell = hRow.addCell();
            cell.setFillColor(PRIMARY_BLUE);
            cell.setText(h);
            styleCellText(cell, true, Color.WHITE, 12.0);
        }

        BigDecimal total = sales.getTotalGrossSales().compareTo(BigDecimal.ZERO) > 0 ? sales.getTotalGrossSales() : BigDecimal.ONE;

        addPaymentRow(table, "UPI (Instant)", sales.getUpiSales(), total, "Instant Direct to Bank");
        addPaymentRow(table, "Cash (Register)", sales.getCashSales(), total, "Same-day Cash on Hand");
        addPaymentRow(table, "Card (POS)", sales.getCardSales(), total, "T+1 Card Network Settlement");
        addPaymentRow(table, "Khata (Customer Credit)", sales.getKhataSales(), total, "Pending Customer Repayment");
    }

    private void createTopProductsSlide(XMLSlideShow ppt, SalesSummaryDto sales) {
        XSLFSlide slide = ppt.createSlide();
        addSlideHeader(slide, "Top Selling SKUs & Revenue Drivers", "Highest volume and revenue contributing items");

        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle(50, 130, 620, 250));

        String[] headers = {"#", "Product Name", "Units Sold", "Total Revenue (₹)", "Performance Rank"};
        XSLFTableRow hRow = table.addRow();
        for (String h : headers) {
            XSLFTableCell cell = hRow.addCell();
            cell.setFillColor(PRIMARY_BLUE);
            cell.setText(h);
            styleCellText(cell, true, Color.WHITE, 12.0);
        }

        List<SalesSummaryDto.TopProductItemDto> tops = sales.getTopSellingProducts();
        int rank = 1;
        if (tops != null && !tops.isEmpty()) {
            for (SalesSummaryDto.TopProductItemDto item : tops) {
                XSLFTableRow row = table.addRow();
                row.addCell().setText(String.valueOf(rank));
                row.addCell().setText(item.getProductName());
                row.addCell().setText(item.getQuantity().stripTrailingZeros().toPlainString());
                row.addCell().setText("₹ " + item.getTotalRevenue().toPlainString());
                row.addCell().setText(rank == 1 ? "★ Top Mover" : "High Velocity");

                for (XSLFTableCell c : row.getCells()) {
                    c.setFillColor(rank % 2 == 0 ? CARD_BG : Color.WHITE);
                    styleCellText(c, false, TEXT_DARK, 11.0);
                }
                rank++;
            }
        } else {
            XSLFTableRow row = table.addRow();
            row.addCell().setText("1");
            row.addCell().setText("Maggi 70g / Aashirvaad Atta / Loose Staples");
            row.addCell().setText("Active");
            row.addCell().setText("₹ " + sales.getTotalGrossSales());
            row.addCell().setText("Standard catalog");
        }
    }

    private void createGstTaxSlabSlide(XMLSlideShow ppt, SalesSummaryDto sales) {
        XSLFSlide slide = ppt.createSlide();
        addSlideHeader(slide, "GST Slab Breakdown & Compliance", "Tax collected across 0%, 5%, 12%, 18% tax categories");

        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle(50, 130, 620, 240));

        String[] headers = {"GST Slab", "Taxable Turnover (₹)", "CGST (₹)", "SGST (₹)", "Total Tax (₹)"};
        XSLFTableRow hRow = table.addRow();
        for (String h : headers) {
            XSLFTableCell cell = hRow.addCell();
            cell.setFillColor(PRIMARY_BLUE);
            cell.setText(h);
            styleCellText(cell, true, Color.WHITE, 12.0);
        }

        List<GstBreakupDto> slabs = sales.getTaxBreakup();
        if (slabs != null && !slabs.isEmpty()) {
            for (GstBreakupDto slab : slabs) {
                XSLFTableRow row = table.addRow();
                row.addCell().setText(slab.getGstRate().stripTrailingZeros().toPlainString() + "% GST");
                row.addCell().setText("₹ " + slab.getTaxableAmount().toPlainString());
                row.addCell().setText("₹ " + slab.getCgstAmount().toPlainString());
                row.addCell().setText("₹ " + slab.getSgstAmount().toPlainString());
                row.addCell().setText("₹ " + slab.getTotalTax().toPlainString());

                for (XSLFTableCell c : row.getCells()) {
                    styleCellText(c, false, TEXT_DARK, 11.0);
                }
            }
        }
    }

    private void createInventoryHealthSlide(XMLSlideShow ppt, StockHealthDto health) {
        XSLFSlide slide = ppt.createSlide();
        addSlideHeader(slide, "Inventory Health & Reorder Radar", "SKUs approaching or below safe buffer levels");

        createKpiCard(slide, 50, 110, 180, 80, "Total Active SKUs", String.valueOf(health.getTotalSkus()), PRIMARY_BLUE);
        createKpiCard(slide, 270, 110, 180, 80, "Stock Buffer Alerts", String.valueOf(health.getLowStockCount()), new Color(211, 47, 47));
        createKpiCard(slide, 490, 110, 180, 80, "Stock Valuation", "₹ " + health.getTotalInventoryValuation(), ACCENT_TEAL);

        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle(50, 210, 620, 240));

        String[] headers = {"SKU", "Item Description", "Current Qty", "Reorder Threshold", "Action"};
        XSLFTableRow hRow = table.addRow();
        for (String h : headers) {
            XSLFTableCell cell = hRow.addCell();
            cell.setFillColor(PRIMARY_BLUE);
            cell.setText(h);
            styleCellText(cell, true, Color.WHITE, 12.0);
        }

        List<ProductDto> lowList = health.getLowStockProducts();
        if (lowList != null && !lowList.isEmpty()) {
            for (ProductDto p : lowList) {
                XSLFTableRow row = table.addRow();
                row.addCell().setText(p.getSku());
                row.addCell().setText(p.getName());
                row.addCell().setText(p.getQuantity().stripTrailingZeros().toPlainString() + " " + p.getUnit());
                row.addCell().setText(p.getReorderLevel().stripTrailingZeros().toPlainString() + " " + p.getUnit());
                row.addCell().setText("Reorder Immediately");

                for (XSLFTableCell c : row.getCells()) {
                    styleCellText(c, false, TEXT_DARK, 10.0);
                }
            }
        } else {
            XSLFTableRow row = table.addRow();
            row.addCell().setText("ALL");
            row.addCell().setText("All inventory levels are healthy above safety threshold.");
            row.addCell().setText("OK");
            row.addCell().setText("OK");
            row.addCell().setText("No Immediate Action Needed");
        }
    }

    private void addSlideHeader(XSLFSlide slide, String title, String subtitle) {
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(40, 20, 640, 65));
        
        XSLFTextParagraph p1 = titleBox.addNewTextParagraph();
        XSLFTextRun r1 = p1.addNewTextRun();
        r1.setText(title);
        r1.setFontSize(22.0);
        r1.setBold(true);
        r1.setFontColor(PRIMARY_BLUE);

        XSLFTextParagraph p2 = titleBox.addNewTextParagraph();
        XSLFTextRun r2 = p2.addNewTextRun();
        r2.setText(subtitle);
        r2.setFontSize(11.0);
        r2.setFontColor(TEXT_MUTED);
    }

    private void createKpiCard(XSLFSlide slide, int x, int y, int w, int h, String title, String value, Color accent) {
        XSLFTextBox card = slide.createTextBox();
        card.setAnchor(new Rectangle(x, y, w, h));
        card.setFillColor(CARD_BG);
        card.setLineColor(BORDER_COLOR);

        XSLFTextParagraph pTitle = card.addNewTextParagraph();
        XSLFTextRun rTitle = pTitle.addNewTextRun();
        rTitle.setText(title);
        rTitle.setFontSize(10.0);
        rTitle.setFontColor(TEXT_MUTED);

        XSLFTextParagraph pVal = card.addNewTextParagraph();
        pVal.setSpaceBefore(8.0);
        XSLFTextRun rVal = pVal.addNewTextRun();
        rVal.setText(value);
        rVal.setFontSize(16.0);
        rVal.setBold(true);
        rVal.setFontColor(accent);
    }

    private void addBullet(XSLFTextBox box, String text) {
        XSLFTextParagraph p = box.addNewTextParagraph();
        p.setSpaceBefore(8.0);
        XSLFTextRun r = p.addNewTextRun();
        r.setText("• " + text);
        r.setFontSize(11.0);
        r.setFontColor(TEXT_DARK);
    }

    private void addPaymentRow(XSLFTable table, String mode, BigDecimal amount, BigDecimal total, String note) {
        BigDecimal pct = amount.multiply(new BigDecimal("100")).divide(total, 1, RoundingMode.HALF_UP);
        XSLFTableRow row = table.addRow();
        row.addCell().setText(mode);
        row.addCell().setText("₹ " + amount.toPlainString());
        row.addCell().setText(pct.toPlainString() + "%");
        row.addCell().setText(note);

        for (XSLFTableCell c : row.getCells()) {
            styleCellText(c, false, TEXT_DARK, 11.0);
        }
    }

    private void styleCellText(XSLFTableCell cell, boolean bold, Color color, double size) {
        if (!cell.getTextParagraphs().isEmpty()) {
            for (XSLFTextParagraph p : cell.getTextParagraphs()) {
                for (XSLFTextRun r : p.getTextRuns()) {
                    r.setBold(bold);
                    r.setFontColor(color);
                    r.setFontSize(size);
                }
            }
        }
    }
}
