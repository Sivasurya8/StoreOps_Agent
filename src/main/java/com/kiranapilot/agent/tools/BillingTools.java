package com.kiranapilot.agent.tools;

import com.kiranapilot.agent.ToolResult;
import com.kiranapilot.billing.BillingService;
import com.kiranapilot.dto.DraftBillDto;
import com.kiranapilot.dto.DraftBillItemDto;
import com.kiranapilot.entity.Bill;
import com.kiranapilot.exception.InsufficientStockException;
import com.kiranapilot.exception.KiranaPilotException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingTools {

    private final BillingService billingService;

    /**
     * Tool: add_bill_item
     * Add an item to the current customer's draft bill.
     */
    public ToolResult addBillItem(Map<String, Object> args, Long chatId) {
        try {
            String product = (String) args.get("product");
            BigDecimal qty = new BigDecimal(String.valueOf(args.get("quantity")));

            DraftBillDto draft = billingService.addItem(chatId, product, qty);
            return ToolResult.success(draft, formatDraftSummary(draft));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error in addBillItem", e);
            return ToolResult.error("Failed to add item to bill: " + e.getMessage());
        }
    }

    /**
     * Tool: update_bill_item
     * Update quantity of an existing item in the draft bill.
     */
    public ToolResult updateBillItem(Map<String, Object> args, Long chatId) {
        try {
            String product = (String) args.get("product");
            BigDecimal qty = new BigDecimal(String.valueOf(args.get("quantity")));

            DraftBillDto draft = billingService.updateItemQuantity(chatId, product, qty);
            return ToolResult.success(draft, formatDraftSummary(draft));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to update item: " + e.getMessage());
        }
    }

    /**
     * Tool: remove_bill_item
     * Remove an item from the draft bill.
     */
    public ToolResult removeBillItem(Map<String, Object> args, Long chatId) {
        try {
            String product = (String) args.get("product");
            DraftBillDto draft = billingService.removeItem(chatId, product);
            return ToolResult.success(draft, formatDraftSummary(draft));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to remove item: " + e.getMessage());
        }
    }

    /**
     * Tool: view_current_draft
     * View items, tax breakup, and total of the current active bill draft.
     */
    public ToolResult viewCurrentDraft(Map<String, Object> args, Long chatId) {
        try {
            DraftBillDto draft = billingService.getOrCreateDraft(chatId);
            if (draft.getItems().isEmpty()) {
                return ToolResult.success(draft, "Current bill draft is empty.");
            }
            return ToolResult.success(draft, formatDraftSummary(draft));
        } catch (Exception e) {
            return ToolResult.error("Failed to view bill draft: " + e.getMessage());
        }
    }

    /**
     * Tool: set_payment_mode
     * Set payment method (CASH, UPI, CARD, KHATA) and optional reference.
     */
    public ToolResult setPaymentMode(Map<String, Object> args, Long chatId) {
        try {
            String mode = (String) args.get("payment_mode");
            String ref = (String) args.get("payment_reference");
            DraftBillDto draft = billingService.setPaymentMode(chatId, mode, ref);
            return ToolResult.success(draft, "Payment mode set to " + draft.getPaymentMode() + (ref != null ? " (Ref: " + ref + ")" : ""));
        } catch (Exception e) {
            return ToolResult.error("Failed to set payment mode: " + e.getMessage());
        }
    }

    /**
     * Tool: finalize_bill
     * Finalize the draft bill: atomically decrements stock, records sale and returns invoice details.
     */
    public ToolResult finalizeBill(Map<String, Object> args, Long chatId) {
        try {
            // Optional overrides passed during finalize
            if (args.get("payment_mode") != null) {
                billingService.setPaymentMode(chatId, (String) args.get("payment_mode"), (String) args.get("payment_reference"));
            }
            if (args.get("customer_name") != null) {
                billingService.setCustomer(chatId, (String) args.get("customer_name"));
            }

            Bill bill = billingService.finalizeBill(chatId);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("✅ Bill #%s finalized successfully!\n", bill.getBillNumber()));
            sb.append(String.format("• Grand Total: ₹%s\n", bill.getGrandTotal()));
            sb.append(String.format("• Taxable: ₹%s | CGST: ₹%s | SGST: ₹%s | Total Tax: ₹%s\n",
                    bill.getTotalTaxable(), bill.getTotalCgst(), bill.getTotalSgst(), bill.getTotalTax()));
            sb.append(String.format("• Payment: %s%s\n", bill.getPaymentMode(), bill.getPaymentReference() != null ? " (" + bill.getPaymentReference() + ")" : ""));
            if (bill.getCustomer() != null) {
                sb.append(String.format("• Customer: %s\n", bill.getCustomer().getName()));
            }

            return ToolResult.success(bill, sb.toString());
        } catch (InsufficientStockException e) {
            return ToolResult.error("🛑 Cannot finalize bill: " + e.getMessage());
        } catch (KiranaPilotException e) {
            return ToolResult.error("Error finalizing bill: " + e.getMessage());
        } catch (Exception e) {
            log.error("Finalize bill failed", e);
            return ToolResult.error("Bill finalization failed: " + e.getMessage());
        }
    }

    /**
     * Tool: cancel_draft_bill
     * Cancel and discard current active draft.
     */
    public ToolResult cancelDraftBill(Map<String, Object> args, Long chatId) {
        try {
            billingService.cancelDraft(chatId);
            return ToolResult.success(null, "Current draft bill has been cancelled and cleared.");
        } catch (Exception e) {
            return ToolResult.error("Failed to cancel draft: " + e.getMessage());
        }
    }

    private String formatDraftSummary(DraftBillDto draft) {
        if (draft.getItems().isEmpty()) {
            return "Current draft bill is empty.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Current Bill Draft:\n");
        int idx = 1;
        for (DraftBillItemDto item : draft.getItems()) {
            sb.append(String.format("%d. %s × %s %s @ ₹%s = ₹%s (incl %s%% GST)\n",
                    idx++, item.getProductName(), item.getQuantity().stripTrailingZeros().toPlainString(),
                    item.getUnit(), item.getUnitPrice(), item.getTotalAmount(), item.getGstRate().stripTrailingZeros().toPlainString()));
        }
        sb.append(String.format("─────────────────────\n"));
        sb.append(String.format("Subtotal (Taxable): ₹%s\n", draft.getTotalTaxable()));
        sb.append(String.format("GST (CGST ₹%s + SGST ₹%s): ₹%s\n", draft.getTotalCgst(), draft.getTotalSgst(), draft.getTotalTax()));
        sb.append(String.format("Grand Total: ₹%s\n", draft.getGrandTotal()));
        sb.append(String.format("Payment Mode: %s\n", draft.getPaymentMode()));
        return sb.toString();
    }
}
