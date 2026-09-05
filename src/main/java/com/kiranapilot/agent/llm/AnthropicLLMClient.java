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
public class AnthropicLLMClient implements LLMClient {

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AnthropicLLMClient(String apiKey, String model, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "claude-3-5-sonnet-20241022";
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public LLMResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            String url = "https://api.anthropic.com/v1/messages";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 2048);

            Optional<ChatMessage> sysMsg = messages.stream().filter(m -> m.getRole() == ChatMessage.Role.SYSTEM).findFirst();
            sysMsg.ifPresent(chatMessage -> requestBody.put("system", chatMessage.getContent()));

            List<Map<String, Object>> msgs = new ArrayList<>();
            for (ChatMessage m : messages) {
                if (m.getRole() == ChatMessage.Role.SYSTEM) continue;

                Map<String, Object> msg = new HashMap<>();
                msg.put("role", (m.getRole() == ChatMessage.Role.USER || m.getRole() == ChatMessage.Role.TOOL) ? "user" : "assistant");

                if (m.getRole() == ChatMessage.Role.TOOL) {
                    msg.put("content", List.of(Map.of(
                            "type", "tool_result",
                            "tool_use_id", m.getToolCallId(),
                            "content", m.getContent()
                    )));
                } else if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    List<Map<String, Object>> contentList = new ArrayList<>();
                    if (m.getContent() != null && !m.getContent().isEmpty()) {
                        contentList.add(Map.of("type", "text", "text", m.getContent()));
                    }
                    for (LLMToolCall tc : m.getToolCalls()) {
                        contentList.add(Map.of(
                                "type", "tool_use",
                                "id", tc.getId(),
                                "name", tc.getName(),
                                "input", tc.getArguments()
                        ));
                    }
                    msg.put("content", contentList);
                } else {
                    msg.put("content", m.getContent());
                }
                msgs.add(msg);
            }
            requestBody.put("messages", msgs);

            if (tools != null && !tools.isEmpty()) {
                List<Map<String, Object>> toolsList = new ArrayList<>();
                for (ToolDefinition t : tools) {
                    toolsList.add(Map.of(
                            "name", t.getName(),
                            "description", t.getDescription(),
                            "input_schema", t.getParameters()
                    ));
                }
                requestBody.put("tools", toolsList);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode contentArray = root.path("content");

            StringBuilder text = new StringBuilder();
            List<LLMToolCall> toolCalls = new ArrayList<>();

            for (JsonNode c : contentArray) {
                String type = c.path("type").asText();
                if ("text".equals(type)) {
                    text.append(c.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    String id = c.path("id").asText();
                    String name = c.path("name").asText();
                    Map<String, Object> input = objectMapper.convertValue(c.path("input"), new TypeReference<Map<String, Object>>() {});
                    toolCalls.add(LLMToolCall.builder().id(id).name(name).arguments(input).build());
                }
            }

            return LLMResponse.builder()
                    .content(text.length() > 0 ? text.toString() : null)
                    .toolCalls(toolCalls)
                    .finishReason(root.path("stop_reason").asText())
                    .build();

        } catch (Exception e) {
            log.error("Anthropic API call failed", e);
            throw new RuntimeException("Anthropic error: " + e.getMessage(), e);
        }
    }
}
