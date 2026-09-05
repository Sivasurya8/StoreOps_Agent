package com.kiranapilot.agent;

import com.kiranapilot.agent.tools.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentToolRegistry {

    private final InventoryTools inventoryTools;
    private final BillingTools billingTools;
    private final KhataTools khataTools;
    private final AnalyticsTools analyticsTools;
    private final DocumentTools documentTools;
    private final PreferenceTools preferenceTools;

    public List<ToolDefinition> getAllToolDefinitions() {
        List<ToolDefinition> list = new ArrayList<>();

        // 1. Inventory Tools
        list.add(ToolDefinition.builder()
                .name("receive_stock")
                .description("Record incoming stock delivery for a product from a supplier/distributor, with optional cost price and MRP update.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "product", Map.of("type", "string", "description", "Name or SKU of the product (e.g., 'Maggi', 'Aashirvaad Atta 5kg', 'Sugar Loose')"),
                                "quantity", Map.of("type", "number", "description", "Quantity received (e.g. 50, 10.5)"),
                                "cost_price", Map.of("type", "number", "description", "Supplier unit cost price in INR (optional)"),
                                "mrp", Map.of("type", "number", "description", "New or current Maximum Retail Price in INR (optional)"),
                                "remarks", Map.of("type", "string", "description", "Distributor or delivery invoice reference (optional)")
                        ),
                        "required", List.of("product", "quantity")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("add_product")
                .description("Add a completely new product SKU to the store's inventory catalog.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string", "description", "Product brand and name (e.g., 'Amul Butter 100g')"),
                                "unit", Map.of("type", "string", "description", "Unit of measure: kg, g, litre, ml, packet, piece, dozen"),
                                "is_packaged", Map.of("type", "boolean", "description", "True if packaged item, false if loose"),
                                "cost_price", Map.of("type", "number", "description", "Cost price in INR"),
                                "mrp", Map.of("type", "number", "description", "Maximum Retail Price in INR"),
                                "selling_price", Map.of("type", "number", "description", "Store selling price in INR"),
                                "initial_quantity", Map.of("type", "number", "description", "Initial starting inventory"),
                                "reorder_level", Map.of("type", "number", "description", "Safety reorder threshold"),
                                "gst_rate", Map.of("type", "number", "description", "GST tax slab: 0, 5, 12, 18, 28"),
                                "hsn_code", Map.of("type", "string", "description", "4-digit or 8-digit HSN code")
                        ),
                        "required", List.of("name", "cost_price", "mrp")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("check_stock")
                .description("Check current available inventory stock quantity, price, and GST details for a specific item.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "product", Map.of("type", "string", "description", "Product name or SKU to check (e.g., 'sugar', 'Maggi', 'atta')")
                        ),
                        "required", List.of("product")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("search_products")
                .description("Search products in store catalog by keywords.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Search keyword")
                        ),
                        "required", List.of("query")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("get_low_stock")
                .description("List all products whose current stock is at or below their reorder threshold level.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Collections.emptyMap()
                ))
                .build());

        // 2. Billing Tools
        list.add(ToolDefinition.builder()
                .name("add_bill_item")
                .description("Add an item with quantity to the active draft customer bill.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "product", Map.of("type", "string", "description", "Product name or SKU (e.g. 'sugar', 'Maggi 70g')"),
                                "quantity", Map.of("type", "number", "description", "Quantity to bill (e.g., 2, 4.5)")
                        ),
                        "required", List.of("product", "quantity")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("update_bill_item")
                .description("Update the quantity of an item already in the active draft bill.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "product", Map.of("type", "string", "description", "Product name"),
                                "quantity", Map.of("type", "number", "description", "New quantity")
                        ),
                        "required", List.of("product", "quantity")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("remove_bill_item")
                .description("Remove/drop an item completely from the active draft bill.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "product", Map.of("type", "string", "description", "Product name to remove")
                        ),
                        "required", List.of("product")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("view_current_draft")
                .description("View current items, GST breakdown, subtotal, and grand total of the draft bill.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Collections.emptyMap()
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("set_payment_mode")
                .description("Set the payment method for the current bill (CASH, UPI, CARD, KHATA) and optional transaction reference.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "payment_mode", Map.of("type", "string", "description", "Payment mode: CASH, UPI, CARD, KHATA"),
                                "payment_reference", Map.of("type", "string", "description", "Transaction reference or UPI ref id (optional)")
                        ),
                        "required", List.of("payment_mode")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("finalize_bill")
                .description("Finalize and complete the current draft bill. Atomically verifies stock, decrements inventory, records sale, and generates bill invoice.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "payment_mode", Map.of("type", "string", "description", "Optional override for payment mode (CASH, UPI, CARD, KHATA)"),
                                "payment_reference", Map.of("type", "string", "description", "Optional UPI/Card reference"),
                                "customer_name", Map.of("type", "string", "description", "Customer name (required if payment mode is KHATA)")
                        )
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("cancel_draft_bill")
                .description("Discard and cancel the current draft bill.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Collections.emptyMap()
                ))
                .build());

        // 3. Khata Tools
        list.add(ToolDefinition.builder()
                .name("add_customer_credit")
                .description("Add credit / debit balance to a customer's Khata account ('put ₹500 on Ramesh's credit').")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "customer_name", Map.of("type", "string", "description", "Customer's name"),
                                "amount", Map.of("type", "number", "description", "Credit amount in INR"),
                                "notes", Map.of("type", "string", "description", "Optional note or reason")
                        ),
                        "required", List.of("customer_name", "amount")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("record_customer_payment")
                .description("Record payment / settlement received from a customer towards their credit ledger balance ('Ramesh paid ₹300').")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "customer_name", Map.of("type", "string", "description", "Customer's name"),
                                "amount", Map.of("type", "number", "description", "Amount paid in INR"),
                                "payment_mode", Map.of("type", "string", "description", "Payment method: CASH, UPI (optional)"),
                                "notes", Map.of("type", "string", "description", "Optional payment note")
                        ),
                        "required", List.of("customer_name", "amount")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("get_customer_balance")
                .description("Get the outstanding Khata credit balance for a customer ('Ramesh's balance?').")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "customer_name", Map.of("type", "string", "description", "Customer's name")
                        ),
                        "required", List.of("customer_name")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("get_customer_statement")
                .description("Get full transaction history / ledger statement for a customer.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "customer_name", Map.of("type", "string", "description", "Customer's name")
                        ),
                        "required", List.of("customer_name")
                ))
                .build());

        // 4. Analytics Tools
        list.add(ToolDefinition.builder()
                .name("get_daily_sales_summary")
                .description("Get daily sales performance, tax collected, cash vs UPI split, and top selling items ('today's sales?', 'close the day').")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "date", Map.of("type", "string", "description", "Date in YYYY-MM-DD format (defaults to today)")
                        )
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("get_stock_health")
                .description("Get inventory stock valuation, out of stock counts, and health overview.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Collections.emptyMap()
                ))
                .build());

        // 5. Document Tools
        list.add(ToolDefinition.builder()
                .name("generate_invoice_pdf")
                .description("Generate and send a clean, GST-correct PDF tax invoice document for the latest or specified bill ('send me that bill as a PDF').")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "bill_number", Map.of("type", "string", "description", "Invoice/Bill number (optional, defaults to latest bill)")
                        )
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("generate_analysis_deck")
                .description("Generate and send a comprehensive PowerPoint analysis presentation (.pptx) with charts and insights ('make this week's sales analysis deck').")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Collections.emptyMap()
                ))
                .build());

        // 6. Preference Tools
        list.add(ToolDefinition.builder()
                .name("set_preference")
                .description("Save owner standing preferences that persist across chat sessions and restarts (e.g. default payment method, default brands).")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "key", Map.of("type", "string", "description", "Preference key (e.g. 'default_payment_mode', 'default_atta')"),
                                "value", Map.of("type", "string", "description", "Preference value (e.g. 'UPI', 'Aashirvaad Atta 5kg')")
                        ),
                        "required", List.of("key", "value")
                ))
                .build());

        list.add(ToolDefinition.builder()
                .name("get_preference")
                .description("Retrieve a stored owner preference.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "key", Map.of("type", "string", "description", "Preference key")
                        ),
                        "required", List.of("key")
                ))
                .build());

        return list;
    }

    public ToolResult executeTool(String toolName, Map<String, Object> args, Long chatId) {
        log.info("Executing Tool '{}' for chatId={} with args={}", toolName, chatId, args);
        if (args == null) args = Collections.emptyMap();

        return switch (toolName) {
            case "receive_stock" -> inventoryTools.receiveStock(args);
            case "add_product" -> inventoryTools.addProduct(args);
            case "check_stock" -> inventoryTools.checkStock(args);
            case "search_products" -> inventoryTools.searchProducts(args);
            case "get_low_stock" -> inventoryTools.getLowStock(args);

            case "add_bill_item" -> billingTools.addBillItem(args, chatId);
            case "update_bill_item" -> billingTools.updateBillItem(args, chatId);
            case "remove_bill_item" -> billingTools.removeBillItem(args, chatId);
            case "view_current_draft" -> billingTools.viewCurrentDraft(args, chatId);
            case "set_payment_mode" -> billingTools.setPaymentMode(args, chatId);
            case "finalize_bill" -> billingTools.finalizeBill(args, chatId);
            case "cancel_draft_bill" -> billingTools.cancelDraftBill(args, chatId);

            case "add_customer_credit" -> khataTools.addCustomerCredit(args);
            case "record_customer_payment" -> khataTools.recordCustomerPayment(args);
            case "get_customer_balance" -> khataTools.getCustomerBalance(args);
            case "get_customer_statement" -> khataTools.getCustomerStatement(args);

            case "get_daily_sales_summary" -> analyticsTools.getDailySalesSummary(args);
            case "get_stock_health" -> analyticsTools.getStockHealth(args);

            case "generate_invoice_pdf" -> documentTools.generateInvoicePdf(args, chatId);
            case "generate_analysis_deck" -> documentTools.generateAnalysisDeck(args);

            case "set_preference" -> preferenceTools.setPreference(args);
            case "get_preference" -> preferenceTools.getPreference(args);
            case "get_all_preferences" -> preferenceTools.getAllPreferences(args);

            default -> ToolResult.error("Unknown tool: " + toolName);
        };
    }
}
