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

        // 1. Receive stock: e.g. "50 packets of Maggi came in, cost 12, MRP 14"
        if (lower.contains("came in") || lower.contains("received") || lower.contains("stock in")) {
            BigDecimal qty = extractNumberBefore(userQuery, "(?:packets|packet|units|unit|kg|g|pcs|piece|of)");
            if (qty == null) qty = extractFirstNumber(userQuery);
            BigDecimal cost = extractNamedNumber(userQuery, "cost");
            BigDecimal mrp = extractNamedNumber(userQuery, "mrp");
            String product = extractProductName(userQuery, List.of("maggi", "sugar", "atta", "salt", "oil", "butter", "surf", "dettol", "colgate", "parle"));

            Map<String, Object> args = new HashMap<>();
            args.put("product", product != null ? product : "Maggi 70g");
            args.put("quantity", qty != null ? qty : new BigDecimal("50"));
            if (cost != null) args.put("cost_price", cost);
            if (mrp != null) args.put("mrp", mrp);

            calls.add(LLMToolCall.builder().id("call_mock_1").name("receive_stock").arguments(args).build());
        }

        // 2. Add new product: "new item: Amul Butter 100g, GST 12%, MRP 62"
        else if (lower.startsWith("new item") || lower.startsWith("add product") || lower.contains("add new item")) {
            BigDecimal mrp = extractNamedNumber(userQuery, "mrp");
            BigDecimal gst = extractNamedNumber(userQuery, "gst");
            String name = userQuery.replaceAll("(?i)(new item|add product|add new item)[:\\s]*", "").split(",")[0].trim();

            Map<String, Object> args = new HashMap<>();
            args.put("name", name.isEmpty() ? "Amul Butter 100g" : name);
            args.put("mrp", mrp != null ? mrp : new BigDecimal("62"));
            args.put("cost_price", mrp != null ? mrp.multiply(new BigDecimal("0.85")) : new BigDecimal("50"));
            if (gst != null) args.put("gst_rate", gst);

            calls.add(LLMToolCall.builder().id("call_mock_add").name("add_product").arguments(args).build());
        }

        // 3. Multi-turn bill creation: "make a bill: 2kg sugar, 1 Aashirvaad atta 5kg, 4 Maggi, UPI"
        else if (lower.contains("make a bill") || lower.contains("cut a bill") || lower.contains("bill:") || lower.contains("new bill")) {
            // Check for payment mode
            String payMode = extractPaymentMode(userQuery);

            // Parse items: "2kg sugar", "1 Aashirvaad atta 5kg", "4 Maggi"
            List<ItemParsed> items = parseBillItems(userQuery);
            int idx = 1;
            for (ItemParsed item : items) {
                calls.add(LLMToolCall.builder()
                        .id("call_item_" + (idx++))
                        .name("add_bill_item")
                        .arguments(Map.of("product", item.name, "quantity", item.qty))
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

        // 4. Mid-bill edits: "drop the butter, make it 6 Maggi" or "remove sugar"
        else if (lower.contains("drop") || lower.contains("remove") || lower.contains("make it") || lower.contains("change to")) {
            if (lower.contains("drop") || lower.contains("remove")) {
                String toDrop = extractDropProduct(userQuery);
                calls.add(LLMToolCall.builder()
                        .id("call_drop_1")
                        .name("remove_bill_item")
                        .arguments(Map.of("product", toDrop))
                        .build());
            }
            if (lower.contains("make it") || lower.contains("make") || lower.contains("change")) {
                BigDecimal qty = extractNumberBefore(userQuery, "(?:maggi|sugar|atta|salt|oil|butter|packets|kg)");
                if (qty == null) qty = extractFirstNumber(userQuery);
                String prod = extractProductName(userQuery, List.of("maggi", "sugar", "atta", "salt", "oil", "butter"));
                calls.add(LLMToolCall.builder()
                        .id("call_upd_1")
                        .name("update_bill_item")
                        .arguments(Map.of("product", prod != null ? prod : "Maggi 70g", "quantity", qty != null ? qty : new BigDecimal("6")))
                        .build());
            }
        }

        // 5. Finalize bill: "finalize", "complete bill", "checkout"
        else if (lower.contains("finalize") || lower.contains("finish bill") || lower.contains("complete the bill") || lower.contains("print bill")) {
            calls.add(LLMToolCall.builder()
                    .id("call_fin_1")
                    .name("finalize_bill")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 6. Stock queries: "how much sugar is left?", "stock of maggi"
        else if (lower.contains("how much") || lower.contains("stock of") || lower.contains("is left")) {
            String prod = extractProductName(userQuery, List.of("sugar", "rice", "dal", "atta", "maggi", "butter", "salt", "oil", "surf"));
            calls.add(LLMToolCall.builder()
                    .id("call_stk_1")
                    .name("check_stock")
                    .arguments(Map.of("product", prod != null ? prod : "sugar"))
                    .build());
        }

        // 7. Low stock / reorder: "what's running out?", "low stock"
        else if (lower.contains("running out") || lower.contains("low stock") || lower.contains("reorder")) {
            calls.add(LLMToolCall.builder()
                    .id("call_low_1")
                    .name("get_low_stock")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 8. Khata credit: "put 500 on Ramesh's credit"
        else if (lower.contains("on") && (lower.contains("credit") || lower.contains("khata"))) {
            BigDecimal amt = extractFirstNumber(userQuery);
            String customer = extractCustomerName(userQuery);
            calls.add(LLMToolCall.builder()
                    .id("call_khata_1")
                    .name("add_customer_credit")
                    .arguments(Map.of("customer_name", customer, "amount", amt != null ? amt : new BigDecimal("500")))
                    .build());
        }

        // 9. Khata payment: "Ramesh paid 300"
        else if (lower.contains("paid") || lower.contains("settled")) {
            BigDecimal amt = extractFirstNumber(userQuery);
            String customer = extractCustomerName(userQuery);
            calls.add(LLMToolCall.builder()
                    .id("call_khata_pay")
                    .name("record_customer_payment")
                    .arguments(Map.of("customer_name", customer, "amount", amt != null ? amt : new BigDecimal("300")))
                    .build());
        }

        // 10. Khata balance: "Ramesh's balance?" / "what is Ramesh's balance?"
        else if (lower.contains("balance") || lower.contains("khata of")) {
            String customer = extractCustomerName(userQuery);
            calls.add(LLMToolCall.builder()
                    .id("call_khata_bal")
                    .name("get_customer_balance")
                    .arguments(Map.of("customer_name", customer))
                    .build());
        }

        // 11. Daily close / today's sales: "today's sales?", "close the day"
        else if (lower.contains("today's sales") || lower.contains("todays sales") || lower.contains("close the day") || lower.contains("daily sales")) {
            calls.add(LLMToolCall.builder()
                    .id("call_sales_1")
                    .name("get_daily_sales_summary")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 12. PDF invoice: "send me that bill as a PDF", "invoice pdf"
        else if (lower.contains("pdf") || lower.contains("invoice")) {
            calls.add(LLMToolCall.builder()
                    .id("call_pdf_1")
                    .name("generate_invoice_pdf")
                    .arguments(Collections.emptyMap())
                    .build());
        }

        // 13. Analysis deck: "make this week's sales analysis deck", "pptx"
        else if (lower.contains("deck") || lower.contains("pptx") || lower.contains("powerpoint") || lower.contains("analysis deck")) {
            calls.add(LLMToolCall.builder()
                    .id("call_pptx_1")
                    .name("generate_analysis_deck")
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
                    .content("I am KiranaPilot, your store operations agent. How can I assist you with stock, billing, Khata, or reports?")
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

    private List<ItemParsed> parseBillItems(String text) {
        List<ItemParsed> list = new ArrayList<>();
        // Split on comma or 'and'
        String[] parts = text.split("[,&]|(?i)\\band\\b");
        for (String p : parts) {
            String clean = p.replaceAll("(?i)(make a bill|cut a bill|bill|upi|cash|card)[:\\s]*", "").trim();
            if (clean.isEmpty()) continue;

            BigDecimal qty = extractFirstNumber(clean);
            if (qty == null) qty = BigDecimal.ONE;

            String prodName = clean.replaceAll("[0-9.]+|kg|g|litre|ml|packets|packet|pcs|piece", "").trim();
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

    private String extractProductName(String text, List<String> candidates) {
        String l = text.toLowerCase();
        for (String c : candidates) {
            if (l.contains(c)) return c;
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
