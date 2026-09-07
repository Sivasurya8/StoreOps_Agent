package com.kiranapilot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiranapilot.agent.AgentOrchestrator;
import com.kiranapilot.entity.ProcessedMessage;
import com.kiranapilot.repository.ProcessedMessageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotService {

    private final AgentOrchestrator agentOrchestrator;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${kiranapilot.telegram.bot-token:}")
    private String botToken;

    @Value("${kiranapilot.telegram.polling-enabled:true}")
    private boolean pollingEnabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollingExecutor;
    private long lastOffset = 0;

    @PostConstruct
    public void init() {
        if (botToken != null) {
            botToken = botToken.trim();
        }
        if (botToken == null || botToken.isEmpty()) {
            log.warn("Telegram bot token is not configured. Telegram bot polling will remain idle.");
            return;
        }

        String masked = botToken.length() > 8 ? botToken.substring(0, 10) + "..." + botToken.substring(botToken.length() - 4) : "***";
        log.info("Telegram Bot configured with token: {}", masked);

        if (pollingEnabled) {
            startPolling();
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
        }
    }

    public synchronized void startPolling() {
        if (running.get()) return;
        running.set(true);
        pollingExecutor = Executors.newSingleThreadExecutor();
        pollingExecutor.submit(this::pollUpdatesLoop);
        log.info("Telegram Bot long polling started.");
    }

    private void pollUpdatesLoop() {
        String url = "https://api.telegram.org/bot" + botToken + "/getUpdates";

        while (running.get()) {
            try {
                String fullUrl = url + "?offset=" + (lastOffset + 1) + "&timeout=20";
                ResponseEntity<String> response = restTemplate.getForEntity(fullUrl, String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    if (root.path("ok").asBoolean()) {
                        for (JsonNode update : root.path("result")) {
                            long updateId = update.path("update_id").asLong();
                            if (updateId > lastOffset) {
                                lastOffset = updateId;
                            }
                            handleUpdate(update, updateId);
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.warn("Telegram polling error: {}. Retrying in 5 seconds...", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public void handleUpdate(JsonNode update, long updateId) {
        try {
            if (!update.has("message")) return;
            JsonNode message = update.path("message");
            long chatId = message.path("chat").path("id").asLong();
            String text = message.path("text").asText(null);

            if (text == null || text.trim().isEmpty()) return;

            // 1. Idempotency Check: Prevent duplicate processing of redelivered updates
            if (processedMessageRepository.existsById(updateId)) {
                log.info("Skipping already processed update ID: {}", updateId);
                return;
            }
            processedMessageRepository.save(ProcessedMessage.builder().updateId(updateId).chatId(chatId).build());

            String cleanText = text.trim();

            // 2. Handle commands
            if (cleanText.equalsIgnoreCase("/start")) {
                sendTextMessage(chatId, """
                        🛒 *Welcome to StoreOps Agent!*
                        Your autonomous AI Supermarket & Retail Operations Assistant.

                        Operate your store in plain, natural speech or tap any button below:
                        • *Receive Stock*: `add stock mouse 10 items` or `50 packets of Maggi came in, cost 12, MRP 14`
                        • *Cut a Bill*: `make a bill 2kg sugar, 1 Aashirvaad atta 5kg, 4 Maggi, UPI`
                        • *Mid-Bill Edit*: `drop the butter, make it 6 Maggi`
                        • *Khata Credit*: `put 500 on Ramesh's credit` | `Ramesh paid 300`
                        • *Reports*: `today's sales?` | `what's running out?`
                        • *Invoices & Decks*: `send me that bill as a PDF` | `make this week's sales analysis deck`

                        Type `/new` anytime to start a fresh chat session.
                        """);
                return;
            }

            if (cleanText.equalsIgnoreCase("/new")) {
                agentOrchestrator.clearSession(chatId);
                sendTextMessage(chatId, "🔄 *Conversation session cleared.* Store memory & standing preferences are preserved. How can I help?");
                return;
            }

            if (cleanText.equalsIgnoreCase("/help")) {
                sendTextMessage(chatId, """
                        📖 *KiranaPilot Commands & Phrasing Help:*
                        • `/start` - Start bot and show introduction
                        • `/new` - Reset current conversation context
                        • `/help` - Show this assistance guide
                        
                        *Natural Language Examples:*
                        1. `50 packets of Maggi came in, cost 12, MRP 14`
                        2. `make a bill 2kg sugar, 4 Maggi, UPI`
                        3. `drop the sugar, make it 6 Maggi`
                        4. `finalize`
                        5. `put 500 on Ramesh's credit`
                        6. `Ramesh's balance?`
                        7. `today's sales?`
                        8. `send me that bill as a PDF`
                        9. `make this week's sales analysis deck`
                        """);
                return;
            }

            // 3. Process with AI Agent Orchestrator
            AgentOrchestrator.AgentExecutionResult result = agentOrchestrator.processMessage(chatId, cleanText);

            if (result.getResponseText() != null && !result.getResponseText().isEmpty()) {
                sendTextMessage(chatId, result.getResponseText());
            }

            // 4. Send Document Attachment if generated
            if (result.getDocumentAttachment() != null && result.getAttachmentFilename() != null) {
                sendDocumentMessage(chatId, result.getDocumentAttachment(), result.getAttachmentFilename(), result.getAttachmentMimeType());
            }

        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    private Map<String, Object> getSuggestionsKeyboard() {
        return Map.of(
                "keyboard", List.of(
                        List.of(Map.of("text", "🛒 Make a Bill"), Map.of("text", "📦 Check Stock")),
                        List.of(Map.of("text", "📥 Receive Stock"), Map.of("text", "👤 Khata Balance")),
                        List.of(Map.of("text", "📊 Today's Sales"), Map.of("text", "🧾 Invoice as PDF")),
                        List.of(Map.of("text", "📈 Analysis Deck"), Map.of("text", "🔄 /new"))
                ),
                "resize_keyboard", true,
                "persistent", true
        );
    }

    public void sendTextMessage(long chatId, String text) {
        if (botToken == null || botToken.trim().isEmpty()) {
            log.info("[Local Bot Text - Chat {}]: {}", chatId, text);
            return;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "parse_mode", "Markdown",
                    "reply_markup", getSuggestionsKeyboard()
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            log.warn("Markdown send failed, falling back to plain text: {}", e.getMessage());
            try {
                String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                Map<String, Object> body = Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "reply_markup", getSuggestionsKeyboard()
                );
                restTemplate.postForEntity(url, new HttpEntity<>(body), String.class);
            } catch (Exception ex) {
                log.error("Failed to send telegram text message", ex);
            }
        }
    }

    public void sendDocumentMessage(long chatId, byte[] fileBytes, String filename, String mimeType) {
        if (botToken == null || botToken.trim().isEmpty()) {
            log.info("[Local Bot Document - Chat {}]: Attached {} ({} bytes)", chatId, filename, fileBytes.length);
            return;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendDocument";

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", String.valueOf(chatId));
            body.add("document", fileResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("Sent document {} to Telegram chat {}", filename, chatId);
        } catch (Exception e) {
            log.error("Failed to send telegram document", e);
        }
    }
}
