package com.kiranapilot.agent.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiranapilot.agent.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
public class GeminiLLMClient implements LLMClient {

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiLLMClient(String apiKey, String model, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = (model != null && !model.isEmpty()) ? model : "gemini-1.5-flash";
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public LLMResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            Map<String, Object> requestBody = new HashMap<>();

            // 1. Separate System Instruction if present
            Optional<ChatMessage> systemMsg = messages.stream()
                    .filter(m -> m.getRole() == ChatMessage.Role.SYSTEM)
                    .findFirst();

            systemMsg.ifPresent(chatMessage -> requestBody.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", chatMessage.getContent()))
            )));

            // 2. Contents
            List<Map<String, Object>> contents = new ArrayList<>();
            for (ChatMessage m : messages) {
                if (m.getRole() == ChatMessage.Role.SYSTEM) continue;

                Map<String, Object> contentMap = new HashMap<>();
                String role = (m.getRole() == ChatMessage.Role.USER) ? "user" : "model";
                contentMap.put("role", role);

                List<Map<String, Object>> parts = new ArrayList<>();

                if (m.getContent() != null && !m.getContent().isEmpty()) {
                    parts.add(Map.of("text", m.getContent()));
                }

                if (m.getToolCalls() != null) {
                    for (LLMToolCall tc : m.getToolCalls()) {
                        parts.add(Map.of("functionCall", Map.of(
                                "name", tc.getName(),
                                "args", tc.getArguments() != null ? tc.getArguments() : Collections.emptyMap()
                        )));
                    }
                }

                if (m.getRole() == ChatMessage.Role.TOOL) {
                    contentMap.put("role", "function");
                    parts.add(Map.of("functionResponse", Map.of(
                            "name", m.getName(),
                            "response", Map.of("content", m.getContent())
                    )));
                }

                contentMap.put("parts", parts);
                contents.add(contentMap);
            }
            requestBody.put("contents", contents);

            // 3. Tools (Function Declarations)
            if (tools != null && !tools.isEmpty()) {
                List<Map<String, Object>> declarations = new ArrayList<>();
                for (ToolDefinition t : tools) {
                    declarations.add(Map.of(
                            "name", t.getName(),
                            "description", t.getDescription(),
                            "parameters", t.getParameters() != null ? t.getParameters() : Collections.emptyMap()
                    ));
                }
                requestBody.put("tools", List.of(Map.of("functionDeclarations", declarations)));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidate = root.path("candidates").get(0);
            JsonNode contentNode = candidate.path("content");
            JsonNode partsNode = contentNode.path("parts");

            StringBuilder textContent = new StringBuilder();
            List<LLMToolCall> toolCalls = new ArrayList<>();

            for (JsonNode part : partsNode) {
                if (part.has("text")) {
                    textContent.append(part.path("text").asText());
                }
                if (part.has("functionCall")) {
                    JsonNode fc = part.path("functionCall");
                    String fnName = fc.path("name").asText();
                    Map<String, Object> args = objectMapper.convertValue(fc.path("args"), new TypeReference<Map<String, Object>>() {});
                    toolCalls.add(LLMToolCall.builder()
                            .id("call_" + UUID.randomUUID().toString().substring(0, 8))
                            .name(fnName)
                            .arguments(args)
                            .build());
                }
            }

            return LLMResponse.builder()
                    .content(textContent.length() > 0 ? textContent.toString() : null)
                    .toolCalls(toolCalls)
                    .finishReason(candidate.path("finishReason").asText())
                    .build();

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Gemini LLM error: " + e.getMessage(), e);
        }
    }
}
