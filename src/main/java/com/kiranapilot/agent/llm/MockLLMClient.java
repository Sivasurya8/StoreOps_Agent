package com.kiranapilot.agent.llm;

import com.kiranapilot.agent.ToolDefinition;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MockLLMClient implements LLMClient {

    @Override
    public LLMResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        // If the last message was a tool result, synthesize a final answer
        ChatMessage last = messages.get(messages.size() - 1);
        if (last.getRole() == ChatMessage.Role.TOOL) {
            return LLMResponse.builder()
                    .content(last.getContent())
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        }

        // Get the latest user query
        String userQuery = messages.stream()
                .filter(m -> m.getRole() == ChatMessage.Role.USER)
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElse("")
                .trim();

        String lower = userQuery.toLowerCase();
        List<LLMToolCall> calls = new ArrayList<>();

        // 1. PDF invoice: "send me that bill as a PDF", "invoice pdf", "🧾 Invoice as PDF" (checked early)
        if (lower.contains("pdf") || lower.equals("🧾 invoice as pdf")) {
            calls.add(LLMToolCall.builder()
                    .id("call_pdf_1")
                    .name("generate_invoice_pdf")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 2. Analysis deck: "make this week's sales analysis deck", "pptx", "📈 Analysis Deck"
        else if (lower.contains("deck") || lower.contains("pptx") || lower.contains("powerpoint") || lower.equals("📈 analysis deck")) {
            calls.add(LLMToolCall.builder()
                    .id("call_pptx_1")
                    .name("generate_analysis_deck")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 3. Receive stock / Add stock (Handles "add stoke mouse 10 items", "50 packets of Maggi came in, cost 12, MRP 14", "stock in 10 mouse", etc.)
        else if (lower.contains("came in") || lower.contains("received") || lower.contains("stock in") ||
            lower.contains("add stoke") || lower.contains("add stock") || lower.startsWith("stock ") || lower.startsWith("stoke ") ||
            lower.startsWith("inward") || (lower.contains("add") && (lower.contains("item") || lower.contains("packet") || lower.contains("kg") || lower.contains("piece")))) {
            
            BigDecimal qty = extractQuantity(userQuery);
            if (qty == null) qty = new BigDecimal("10");

            BigDecimal cost = extractNamedNumber(userQuery, "cost");
            BigDecimal mrp = extractNamedNumber(userQuery, "mrp");
            String product = extractGenericProductForStock(userQuery);

            Map<String, Object> args = new HashMap<>();
            args.put("product", product.toLowerCase());
            args.put("quantity", qty);
            if (cost != null) args.put("cost_price", cost);
            if (mrp != null) args.put("mrp", mrp);

            calls.add(LLMToolCall.builder().id("call_stock_recv").name("receive_stock").arguments(args).build());
        }

        // 4. Add new product: "new item: Amul Butter 100g, GST 12%, MRP 62"
        else if (lower.startsWith("new item") || lower.startsWith("add product") || lower.contains("add new item")) {
            BigDecimal mrp = extractNamedNumber(userQuery, "mrp");
            BigDecimal gst = extractNamedNumber(userQuery, "gst");
            String name = userQuery.replaceAll("(?i)(new item|add product|add new item)[:\\s]*", "").split(",")[0].trim();

            Map<String, Object> args = new HashMap<>();
            args.put("name", name.isEmpty() ? "New Product" : name);
            args.put("mrp", mrp != null ? mrp : new BigDecimal("62"));
            args.put("cost_price", mrp != null ? mrp.multiply(new BigDecimal("0.85")) : new BigDecimal("50"));
            if (gst != null) args.put("gst_rate", gst);

            calls.add(LLMToolCall.builder().id("call_mock_add").name("add_product").arguments(args).build());
        }

        // 5. Multi-turn bill creation: "make a bill: 2kg sugar, 1 Aashirvaad atta 5kg, 4 Maggi, UPI", "bill 2 mouse", "cut a bill"
        else if (lower.contains("make a bill") || lower.contains("cut a bill") || lower.contains("bill:") || lower.contains("new bill") ||
                 lower.startsWith("bill ") || lower.startsWith("make bill") || lower.equals("🛒 make a bill")) {
            
            String payMode = extractPaymentMode(userQuery);
            List<ItemParsed> items = parseBillItems(userQuery);
            int idx = 1;
            for (ItemParsed item : items) {
                calls.add(LLMToolCall.builder()
                        .id("call_item_" + (idx++))
                        .name("add_bill_item")
                        .arguments(Map.of("product", item.name.toLowerCase(), "quantity", item.qty))
                        .build());
            }
            if (payMode != null) {
                calls.add(LLMToolCall.builder()
                        .id("call_pay_" + (idx++))
                        .name("set_payment_mode")
                        .arguments(Map.of("payment_mode", payMode))
                        .build());
            }
        }

        // 6. Mid-bill edits: "drop the butter, make it 6 Maggi" or "remove sugar"
        else if (lower.contains("drop") || lower.contains("remove") || lower.contains("make it") || lower.contains("change to")) {
            if (lower.contains("drop") || lower.contains("remove")) {
                String toDrop = extractDropProduct(userQuery);
                calls.add(LLMToolCall.builder()
                        .id("call_drop_1")
                        .name("remove_bill_item")
                        .arguments(Map.of("product", toDrop.toLowerCase()))
                        .build());
            }
            if (lower.contains("make it") || lower.contains("make") || lower.contains("change")) {
                BigDecimal qty = extractNumberBefore(userQuery, "(?:maggi|sugar|atta|salt|oil|butter|packets|kg|piece|items)");
                if (qty == null) qty = extractFirstNumber(userQuery);
                String prod = extractProductName(userQuery);
                calls.add(LLMToolCall.builder()
                        .id("call_upd_1")
                        .name("update_bill_item")
                        .arguments(Map.of("product", (prod != null ? prod : "Maggi 70g").toLowerCase(), "quantity", qty != null ? qty : new BigDecimal("6")))
                        .build());
            }
        }

        // 7. Finalize bill: "finalize", "complete bill", "checkout"
        else if (lower.contains("finalize") || lower.contains("finish bill") || lower.contains("complete the bill") || lower.contains("print bill")) {
            calls.add(LLMToolCall.builder()
                    .id("call_fin_1")
                    .name("finalize_bill")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 8. Stock queries: "how much sugar is left?", "stock of maggi", "check stock", "view stock"
        else if (lower.contains("how much") || lower.contains("stock of") || lower.contains("is left") || lower.equals("check stock") || lower.equals("📦 check stock") || lower.contains("list stock")) {
            String prod = extractProductName(userQuery);
            if (prod != null && !prod.trim().isEmpty() && !prod.equalsIgnoreCase("stock")) {
                calls.add(LLMToolCall.builder()
                        .id("call_stk_1")
                        .name("check_stock")
                        .arguments(Map.of("product", prod.toLowerCase()))
                        .build());
            } else {
                calls.add(LLMToolCall.builder()
                        .id("call_stk_all")
                        .name("search_products")
                        .arguments(Map.of("query", ""))
                        .build());
            }
        }

        // 9. Low stock / reorder: "what's running out?", "low stock"
        else if (lower.contains("running out") || lower.contains("low stock") || lower.contains("reorder")) {
            calls.add(LLMToolCall.builder()
                    .id("call_low_1")
                    .name("get_low_stock")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 10. Khata credit: "put 500 on Ramesh's credit"
        else if (lower.contains("on") && (lower.contains("credit") || lower.contains("khata"))) {
            BigDecimal amt = extractFirstNumber(userQuery);
            String customer = extractCustomerName(userQuery);
            calls.add(LLMToolCall.builder()
                    .id("call_khata_1")
                    .name("add_customer_credit")
                    .arguments(Map.of("customer_name", customer, "amount", amt != null ? amt : new BigDecimal("500")))
                    .build());
        }

        // 11. Khata payment: "Ramesh paid 300"
        else if (lower.contains("paid") || lower.contains("settled")) {
            BigDecimal amt = extractFirstNumber(userQuery);
            String customer = extractCustomerName(userQuery);
            calls.add(LLMToolCall.builder()
                    .id("call_khata_pay")
                    .name("record_customer_payment")
                    .arguments(Map.of("customer_name", customer, "amount", amt != null ? amt : new BigDecimal("300")))
                    .build());
        }

        // 12. Khata balance: "Ramesh's balance?" / "what is Ramesh's balance?" / "Khata Balance"
        else if (lower.contains("balance") || lower.contains("khata") || lower.equals("👤 khata balance")) {
            String customer = extractCustomerName(userQuery);
            calls.add(LLMToolCall.builder()
                    .id("call_khata_bal")
                    .name("get_customer_balance")
                    .arguments(Map.of("customer_name", customer != null ? customer : "Ramesh"))
                    .build());
        }

        // 13. Daily close / today's sales: "today's sales?", "close the day", "📊 Today's Sales"
        else if (lower.contains("today's sales") || lower.contains("todays sales") || lower.contains("close the day") || lower.contains("daily sales") || lower.equals("📊 today's sales")) {
            calls.add(LLMToolCall.builder()
                    .id("call_sales_1")
                    .name("get_daily_sales_summary")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 14. Standing preferences: "always assume UPI unless I say cash" or "default atta = Aashirvaad 5kg"
        else if (lower.contains("always assume") || lower.contains("default")) {
            if (lower.contains("upi")) {
                calls.add(LLMToolCall.builder()
                        .id("call_pref_1")
                        .name("set_preference")
                        .arguments(Map.of("key", "default_payment_mode", "value", "UPI"))
                        .build());
            } else if (lower.contains("atta")) {
                calls.add(LLMToolCall.builder()
                        .id("call_pref_2")
                        .name("set_preference")
                        .arguments(Map.of("key", "default_atta", "value", "Aashirvaad Atta 5kg"))
                        .build());
            }
        }

        // 15. Ambiguity test: "add atta" without quantity or variant
        else if (lower.equals("add atta") || lower.equals("atta")) {
            return LLMResponse.builder()
                    .content("Which atta would you like to add — Aashirvaad Atta 5kg, Aashirvaad Atta 10kg, or Chakki Atta Loose? Also please specify the quantity.")
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        }

        if (calls.isEmpty()) {
            return LLMResponse.builder()
                    .content("""
                            👋 *Namaste! I am KiranaPilot, your store operations agent.*

                            You can speak naturally to me like a real shopkeeper:
                            • 📥 *Receive Stock*: `add stock mouse 10 items` or `50 packets of Maggi came in, cost 12, MRP 14`
                            • 🛒 *Cut a Bill*: `make a bill 2kg sugar, 4 Maggi, UPI`
                            • 👤 *Customer Khata*: `put 500 on Ramesh's credit` or `Ramesh paid 300`
                            • 📊 *Reports*: `today's sales?` or `what's running out?`
                            • 🧾 *Invoices*: `send me that bill as a PDF`

                            You can also use the quick buttons below! 👇
                            """)
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        }

        return LLMResponse.builder()
                .content(null)
                .toolCalls(calls)
                .finishReason("tool_calls")
                .build();
    }

    private static class ItemParsed {
        String name;
        BigDecimal qty;
        ItemParsed(String n, BigDecimal q) { this.name = n; this.qty = q; }
    }

    private String extractGenericProductForStock(String text) {
        String specific = extractProductName(text);
        if (specific != null) {
            return specific;
        }
        // e.g. "add stoke mouse 10 items" -> "mouse"
        // e.g. "50 packets of Maggi came in" -> "maggi"
        String clean = text.replaceAll("(?i)(add stoke|add stock|came in|received|stock in|inward|items|item|packets|packet|pieces|piece|pcs|cost|mrp|cost price|rs|inr|₹|\\d+)+", " ").trim();
        clean = clean.replaceAll("\\s+", " ").trim();
        if (clean.equalsIgnoreCase("of") || clean.isEmpty()) {
            return "Item";
        }
        if (clean.toLowerCase().startsWith("of ")) {
            clean = clean.substring(3).trim();
        }
        return clean.isEmpty() ? "Item" : clean;
    }

    private BigDecimal extractQuantity(String s) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(s);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        return new BigDecimal("1");
    }

    private List<ItemParsed> parseBillItems(String text) {
        List<ItemParsed> list = new ArrayList<>();
        String[] parts = text.split("[,&]|(?i)\\band\\b");
        for (String p : parts) {
            String clean = p.replaceAll("(?i)(make a bill|cut a bill|bill|make bill|upi|cash|card)[:\\s]*", "").trim();
            if (clean.isEmpty()) continue;

            BigDecimal qty = extractFirstNumber(clean);
            if (qty == null) qty = BigDecimal.ONE;

            String prodName = clean.replaceAll("[0-9.]+|kg|g|litre|ml|packets|packet|pcs|piece|items|item", "").trim();
            if (!prodName.isEmpty()) {
                list.add(new ItemParsed(prodName, qty));
            }
        }
        if (list.isEmpty()) {
            list.add(new ItemParsed("Maggi 70g", new BigDecimal("4")));
        }
        return list;
    }

    private BigDecimal extractFirstNumber(String s) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(s);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        return null;
    }

    private BigDecimal extractNumberBefore(String s, String tokenRegex) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*" + tokenRegex, Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        return null;
    }

    private BigDecimal extractNamedNumber(String s, String key) {
        Matcher m = Pattern.compile(key + "[^0-9]*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        return null;
    }

    private String extractProductName(String text) {
        String l = text.toLowerCase();
        List<String> candidates = List.of("maggi", "sugar", "rice", "dal", "atta", "butter", "salt", "oil", "surf", "dettol", "colgate", "parle", "mouse", "keyboard");
        for (String c : candidates) {
            if (l.contains(c)) return c;
        }
        Matcher m = Pattern.compile("(?:stock of|how much|about|check)\\s+([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractDropProduct(String text) {
        Matcher m = Pattern.compile("(?:drop|remove)\\s+(?:the\\s+)?([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "butter";
    }

    private String extractCustomerName(String text) {
        Matcher m = Pattern.compile("([A-Z][a-z]+)(?:'s|\\s+credit|\\s+paid|\\s+balance)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "Ramesh";
    }

    private String extractPaymentMode(String text) {
        String l = text.toLowerCase();
        if (l.contains("upi")) return "UPI";
        if (l.contains("cash")) return "CASH";
        if (l.contains("card")) return "CARD";
        if (l.contains("khata") || l.contains("credit")) return "KHATA";
        return null;
    }
}
