package com.kiranapilot.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiranapilot.agent.llm.*;
import com.kiranapilot.memory.OwnerPreferenceService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrator {

    private final AgentToolRegistry toolRegistry;
    private final LLMClient llmClient;
    private final OwnerPreferenceService preferenceService;
    private final ObjectMapper objectMapper;

    // Chat history per session (chatId -> list of messages)
    private final Map<Long, List<ChatMessage>> sessionHistories = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    @Builder
    public static class AgentExecutionResult {
        private String responseText;
        private byte[] documentAttachment;
        private String attachmentFilename;
        private String attachmentMimeType;
    }

    public void clearSession(Long chatId) {
        sessionHistories.remove(chatId);
        log.info("Cleared conversation session for chat {}", chatId);
    }

    public AgentExecutionResult processMessage(Long chatId, String userMessage) {
        log.info("Processing message for chat {}: '{}'", chatId, userMessage);

        List<ChatMessage> history = sessionHistories.computeIfAbsent(chatId, k -> new ArrayList<>());

        // Build system prompt with grounding rules and persistent memory
        if (history.isEmpty()) {
            history.add(ChatMessage.system(buildSystemPrompt()));
        }

        // Add user message
        history.add(ChatMessage.user(userMessage));

        List<ToolDefinition> tools = toolRegistry.getAllToolDefinitions();

        byte[] pendingAttachment = null;
        String pendingFilename = null;
        String pendingMimeType = null;
        String finalResponseText = null;

        int maxIterations = 6;
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            LLMResponse llmResponse;
            try {
                llmResponse = llmClient.chat(history, tools);
            } catch (Exception e) {
                log.error("Error during LLM chat completion", e);
                return AgentExecutionResult.builder()
                        .responseText("⚠️ Error processing request with AI engine: " + e.getMessage())
                        .build();
            }

            if (llmResponse.hasToolCalls()) {
                // Record assistant's tool call in history
                history.add(ChatMessage.assistantWithTools(llmResponse.getContent(), llmResponse.getToolCalls()));

                // Execute each tool call
                for (LLMToolCall toolCall : llmResponse.getToolCalls()) {
                    ToolResult result = toolRegistry.executeTool(toolCall.getName(), toolCall.getArguments(), chatId);

                    if (result.getDocumentAttachment() != null) {
                        pendingAttachment = result.getDocumentAttachment();
                        pendingFilename = result.getAttachmentFilename();
                        pendingMimeType = result.getAttachmentMimeType();
                    }

                    String resultString;
                    try {
                        resultString = result.getMessage() != null ? result.getMessage() : objectMapper.writeValueAsString(result.getData());
                    } catch (Exception e) {
                        resultString = String.valueOf(result.getData());
                    }

                    // Feed result back to conversation history
                    history.add(ChatMessage.tool(toolCall.getId(), toolCall.getName(), resultString));
                }
            } else {
                // Final response reached
                finalResponseText = llmResponse.getContent();
                if (finalResponseText != null) {
                    history.add(ChatMessage.assistant(finalResponseText));
                }
                break;
            }
        }

        // Prune history if it exceeds 30 messages to manage context
        if (history.size() > 30) {
            ChatMessage system = history.get(0);
            List<ChatMessage> recent = history.subList(history.size() - 20, history.size());
            List<ChatMessage> pruned = new ArrayList<>();
            pruned.add(system);
            pruned.addAll(recent);
            sessionHistories.put(chatId, pruned);
        }

        if (finalResponseText == null && pendingAttachment != null) {
            finalResponseText = "Here is your requested document.";
        } else if (finalResponseText == null) {
            finalResponseText = "Request processed.";
        }

        return AgentExecutionResult.builder()
                .responseText(finalResponseText)
                .documentAttachment(pendingAttachment)
                .attachmentFilename(pendingFilename)
                .attachmentMimeType(pendingMimeType)
                .build();
    }

    private String buildSystemPrompt() {
        Map<String, String> prefs = preferenceService.getAllPreferences();
        StringBuilder sb = new StringBuilder();
        sb.append("You are KiranaPilot, an autonomous AI supermarket & kirana operations agent.\n");
        sb.append("The owner operates the entire shop from this chat window. You reason over requests and orchestrate domain tools.\n\n");

        sb.append("CRITICAL DOMAIN & ARCHITECTURE RULES:\n");
        sb.append("1. GROUNDING: Product names, stock quantities, cost prices, selling prices, GST rates, and HSN codes MUST come strictly from database tools. NEVER invent or assume prices or stock.\n");
        sb.append("2. OVERSELL GUARD: Selling more stock than available is strictly refused by the backend tool layer. If stock is insufficient, report it clearly to the owner.\n");
        sb.append("3. MULTI-TURN BILLING: Bills build across multiple messages (add, update, remove items). Stock is decremented ONLY when finalized. Mid-build edits like 'drop butter, make it 6 Maggi' must be supported.\n");
        sb.append("4. GST MATH: GST rates (0%, 5%, 12%, 18%) and intra-state CGST/SGST splits are calculated deterministically by tools. Always present clean totals.\n");
        sb.append("5. KHATA (CREDIT LEDGER): Maintain customer balances accurately. 'put ₹500 on Ramesh's credit' adds debit; 'Ramesh paid ₹300' records payment settlement.\n");
        sb.append("6. AMBIGUITY: When a request is ambiguous (e.g. 'add atta' without variant), ask a clarifying question rather than guessing.\n");
        sb.append("7. PERSISTENT OWNER PREFERENCES:\n");
        sb.append(String.format("   • Store Name: %s\n", preferenceService.getStoreName()));
        sb.append(String.format("   • GSTIN: %s\n", preferenceService.getStoreGstin()));
        sb.append(String.format("   • Default Payment Mode: %s\n", preferenceService.getDefaultPaymentMode()));
        for (Map.Entry<String, String> entry : prefs.entrySet()) {
            sb.append(String.format("   • %s: %s\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\nRespond politely, concisely, and in professional Indian supermarket shopkeeper phrasing.");
        return sb.toString();
    }
}
