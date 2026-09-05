package com.kiranapilot.agent.tools;

import com.kiranapilot.agent.ToolResult;
import com.kiranapilot.dto.KhataSummaryDto;
import com.kiranapilot.exception.KiranaPilotException;
import com.kiranapilot.khata.KhataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KhataTools {

    private final KhataService khataService;

    /**
     * Tool: add_customer_credit
     * Add credit / debit to a customer's Khata account ("put ₹500 on Ramesh's credit").
     */
    public ToolResult addCustomerCredit(Map<String, Object> args) {
        try {
            String customer = (String) args.get("customer_name");
            BigDecimal amount = new BigDecimal(String.valueOf(args.get("amount")));
            String notes = (String) args.get("notes");

            KhataSummaryDto summary = khataService.addCredit(customer, amount, null, notes);
            return ToolResult.success(summary, String.format("Added ₹%s to %s's credit ledger. Total outstanding balance is now ₹%s.",
                    amount.toPlainString(), summary.getCustomerName(), summary.getCurrentBalance().toPlainString()));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error adding credit", e);
            return ToolResult.error("Failed to add credit: " + e.getMessage());
        }
    }

    /**
     * Tool: record_customer_payment
     * Record payment / settlement from a customer ("Ramesh paid ₹300").
     */
    public ToolResult recordCustomerPayment(Map<String, Object> args) {
        try {
            String customer = (String) args.get("customer_name");
            BigDecimal amount = new BigDecimal(String.valueOf(args.get("amount")));
            String mode = args.get("payment_mode") != null ? (String) args.get("payment_mode") : "CASH";
            String notes = (String) args.get("notes");

            KhataSummaryDto summary = khataService.recordPayment(customer, amount, mode, notes);
            return ToolResult.success(summary, String.format("Recorded payment of ₹%s (%s) from %s. Remaining credit balance: ₹%s.",
                    amount.toPlainString(), mode.toUpperCase(), summary.getCustomerName(), summary.getCurrentBalance().toPlainString()));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error recording payment", e);
            return ToolResult.error("Failed to record payment: " + e.getMessage());
        }
    }

    /**
     * Tool: get_customer_balance
     * Get the current outstanding credit balance for a customer.
     */
    public ToolResult getCustomerBalance(Map<String, Object> args) {
        try {
            String customer = (String) args.get("customer_name");
            KhataSummaryDto summary = khataService.getStatement(customer);
            return ToolResult.success(summary, String.format("Customer: %s | Outstanding Credit Balance: ₹%s",
                    summary.getCustomerName(), summary.getCurrentBalance().toPlainString()));
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to check balance: " + e.getMessage());
        }
    }

    /**
     * Tool: get_customer_statement
     * Get transaction statement for a customer's Khata account.
     */
    public ToolResult getCustomerStatement(Map<String, Object> args) {
        try {
            String customer = (String) args.get("customer_name");
            KhataSummaryDto summary = khataService.getStatement(customer);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📖 Khata Statement for %s (Current Balance: ₹%s):\n", summary.getCustomerName(), summary.getCurrentBalance()));
            for (KhataSummaryDto.KhataEntryDto tx : summary.getRecentTransactions()) {
                sb.append(String.format("• %s: ₹%s (%s) | Balance: ₹%s | %s\n",
                        tx.getType().equals("CREDIT_GIVEN") ? "Debit/Credit" : "Payment",
                        tx.getAmount(), tx.getPaymentMode() != null ? tx.getPaymentMode() : "ACC",
                        tx.getBalanceAfter(), tx.getNotes() != null ? tx.getNotes() : ""));
            }
            return ToolResult.success(summary, sb.toString());
        } catch (KiranaPilotException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to retrieve statement: " + e.getMessage());
        }
    }
}
