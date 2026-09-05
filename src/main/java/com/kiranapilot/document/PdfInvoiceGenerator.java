package com.kiranapilot.document;

import com.kiranapilot.entity.Bill;
import com.kiranapilot.entity.BillItem;
import com.kiranapilot.gst.GstCalculator;
import com.kiranapilot.memory.OwnerPreferenceService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfInvoiceGenerator {

    private final OwnerPreferenceService preferenceService;
    private final GstCalculator gstCalculator;

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 37, 41));
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(108, 117, 125));
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(33, 37, 41));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(33, 37, 41));
    private static final Font CELL_BOLD_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(33, 37, 41));
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(13, 110, 253));

    public byte[] generateInvoicePdf(Bill bill) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter.getInstance(document, baos);
            document.open();

            // 1. Header Box
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{60f, 40f});

            // Store Info (Left)
            PdfPCell storeCell = new PdfPCell();
            storeCell.setBorder(Rectangle.NO_BORDER);
            storeCell.addElement(new Paragraph(preferenceService.getStoreName(), TITLE_FONT));
            storeCell.addElement(new Paragraph(preferenceService.getStoreAddress(), SUBTITLE_FONT));
            storeCell.addElement(new Paragraph("GSTIN: " + preferenceService.getStoreGstin() + " | Ph: " + preferenceService.getStorePhone(), SUBTITLE_FONT));
            storeCell.addElement(new Paragraph("State: Karnataka (Code: 29)", SUBTITLE_FONT));
            headerTable.addCell(storeCell);

            // Invoice Info (Right)
            PdfPCell invMetaCell = new PdfPCell();
            invMetaCell.setBorder(Rectangle.NO_BORDER);
            invMetaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph invTitle = new Paragraph("TAX INVOICE", new Font(Font.HELVETICA, 14, Font.BOLD, new Color(13, 110, 253)));
            invTitle.setAlignment(Element.ALIGN_RIGHT);
            invMetaCell.addElement(invTitle);

            Paragraph invNum = new Paragraph("Invoice #: " + bill.getBillNumber(), CELL_BOLD_FONT);
            invNum.setAlignment(Element.ALIGN_RIGHT);
            invMetaCell.addElement(invNum);

            String dateStr = bill.getCreatedAt() != null ? 
                    bill.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")) : 
                    "N/A";
            Paragraph invDate = new Paragraph("Date: " + dateStr, CELL_FONT);
            invDate.setAlignment(Element.ALIGN_RIGHT);
            invMetaCell.addElement(invDate);

            Paragraph payMode = new Paragraph("Payment: " + bill.getPaymentMode() + (bill.getPaymentReference() != null ? " (" + bill.getPaymentReference() + ")" : ""), CELL_FONT);
            payMode.setAlignment(Element.ALIGN_RIGHT);
            invMetaCell.addElement(payMode);

            headerTable.addCell(invMetaCell);
            document.add(headerTable);

            // Divider Line
            Paragraph divider = new Paragraph(" ");
            divider.setSpacingAfter(4);
            document.add(divider);

            // 2. Customer Section
            PdfPTable custTable = new PdfPTable(1);
            custTable.setWidthPercentage(100);
            PdfPCell custCell = new PdfPCell();
            custCell.setBackgroundColor(new Color(245, 247, 250));
            custCell.setPadding(6);
            custCell.setBorderColor(new Color(220, 224, 230));
            String custText = "Billed To: " + (bill.getCustomer() != null ? bill.getCustomer().getName() : "Cash Customer");
            if (bill.getCustomer() != null && bill.getCustomer().getPhone() != null) {
                custText += " | Ph: " + bill.getCustomer().getPhone();
            }
            custCell.addElement(new Paragraph(custText, CELL_BOLD_FONT));
            custTable.addCell(custCell);
            document.add(custTable);

            Paragraph sp1 = new Paragraph(" ");
            sp1.setSpacingAfter(6);
            document.add(sp1);

            // 3. Itemized GST Table
            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{5f, 28f, 10f, 8f, 7f, 10f, 10f, 7f, 7f, 12f});

            String[] headers = {"#", "Item Description", "HSN", "Qty", "Unit", "Rate (₹)", "Taxable (₹)", "CGST", "SGST", "Total (₹)"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
                cell.setBackgroundColor(new Color(13, 110, 253));
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            int index = 1;
            for (BillItem item : bill.getItems()) {
                addTableCell(table, String.valueOf(index++), Element.ALIGN_CENTER, CELL_FONT);
                addTableCell(table, item.getProductName(), Element.ALIGN_LEFT, CELL_BOLD_FONT);
                addTableCell(table, item.getHsnCode() != null ? item.getHsnCode() : "N/A", Element.ALIGN_CENTER, CELL_FONT);
                addTableCell(table, item.getQuantity().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT, CELL_FONT);
                addTableCell(table, item.getUnit(), Element.ALIGN_CENTER, CELL_FONT);
                addTableCell(table, item.getUnitPrice().toPlainString(), Element.ALIGN_RIGHT, CELL_FONT);
                addTableCell(table, item.getTaxableValue().toPlainString(), Element.ALIGN_RIGHT, CELL_FONT);
                addTableCell(table, item.getCgstAmount().toPlainString(), Element.ALIGN_RIGHT, CELL_FONT);
                addTableCell(table, item.getSgstAmount().toPlainString(), Element.ALIGN_RIGHT, CELL_FONT);
                addTableCell(table, item.getTotalAmount().toPlainString(), Element.ALIGN_RIGHT, CELL_BOLD_FONT);
            }

            document.add(table);

            // 4. Totals & Tax Summary
            Paragraph sp2 = new Paragraph(" ");
            sp2.setSpacingAfter(6);
            document.add(sp2);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{55f, 45f});

            // Terms / Signatory (Left)
            PdfPCell termsCell = new PdfPCell();
            termsCell.setBorder(Rectangle.BOX);
            termsCell.setBorderColor(new Color(220, 224, 230));
            termsCell.setPadding(8);
            termsCell.addElement(new Paragraph("Terms & Conditions:", CELL_BOLD_FONT));
            termsCell.addElement(new Paragraph("1. Goods once sold will not be taken back without valid bill.", CELL_FONT));
            termsCell.addElement(new Paragraph("2. This is a computer generated GST tax invoice.", CELL_FONT));
            termsCell.addElement(new Paragraph(" \nFor " + preferenceService.getStoreName() + "\n\nAuthorised Signatory", CELL_FONT));
            summaryTable.addCell(termsCell);

            // Totals Box (Right)
            PdfPCell totalsCell = new PdfPCell();
            totalsCell.setBorder(Rectangle.BOX);
            totalsCell.setBorderColor(new Color(220, 224, 230));
            totalsCell.setPadding(8);

            PdfPTable tTable = new PdfPTable(2);
            tTable.setWidthPercentage(100);
            tTable.setWidths(new float[]{60f, 40f});

            addSummaryRow(tTable, "Total Taxable Value:", "₹ " + bill.getTotalTaxable().toPlainString(), false);
            addSummaryRow(tTable, "Central GST (CGST):", "₹ " + bill.getTotalCgst().toPlainString(), false);
            addSummaryRow(tTable, "State GST (SGST):", "₹ " + bill.getTotalSgst().toPlainString(), false);
            addSummaryRow(tTable, "Total GST Tax:", "₹ " + bill.getTotalTax().toPlainString(), false);
            addSummaryRow(tTable, "Grand Total:", "₹ " + bill.getGrandTotal().toPlainString(), true);

            totalsCell.addElement(tTable);
            summaryTable.addCell(totalsCell);

            document.add(summaryTable);
            document.close();

            log.info("Generated GST PDF invoice for Bill #{}", bill.getBillNumber());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice PDF", e);
            throw new RuntimeException("Invoice PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void addTableCell(PdfPTable table, String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(230, 234, 240));
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, boolean isTotal) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, isTotal ? TOTAL_FONT : CELL_FONT));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        lCell.setPadding(3);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, isTotal ? TOTAL_FONT : CELL_BOLD_FONT));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPadding(3);
        table.addCell(vCell);
    }
}
