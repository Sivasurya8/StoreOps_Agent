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
public class OpenAILLMClient implements LLMClient {

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String endpoint;

    public OpenAILLMClient(String apiKey, String model, String endpoint, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "gpt-4o";
        this.endpoint = endpoint != null ? endpoint : "https://api.openai.com/v1/chat/completions";
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public LLMResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);

            List<Map<String, Object>> msgs = new ArrayList<>();
            for (ChatMessage m : messages) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("role", m.getRole().name().toLowerCase());
                msgMap.put("content", m.getContent());

                if (m.getRole() == ChatMessage.Role.TOOL) {
                    msgMap.put("tool_call_id", m.getToolCallId());
                    msgMap.put("name", m.getName());
                }

                if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    List<Map<String, Object>> toolCallsJson = new ArrayList<>();
                    for (LLMToolCall tc : m.getToolCalls()) {
                        Map<String, Object> tcJson = new HashMap<>();
                        tcJson.put("id", tc.getId());
                        tcJson.put("type", "function");
                        Map<String, Object> fn = new HashMap<>();
                        fn.put("name", tc.getName());
                        fn.put("arguments", objectMapper.writeValueAsString(tc.getArguments()));
                        tcJson.put("function", fn);
                        toolCallsJson.add(tcJson);
                    }
                    msgMap.put("tool_calls", toolCallsJson);
                }
                msgs.add(msgMap);
            }
            requestBody.put("messages", msgs);

            if (tools != null && !tools.isEmpty()) {
                List<Map<String, Object>> toolsJson = new ArrayList<>();
                for (ToolDefinition t : tools) {
                    Map<String, Object> tJson = new HashMap<>();
                    tJson.put("type", "function");
                    Map<String, Object> fn = new HashMap<>();
                    fn.put("name", t.getName());
                    fn.put("description", t.getDescription());
                    fn.put("parameters", t.getParameters());
                    tJson.put("function", fn);
                    toolsJson.add(tJson);
                }
                requestBody.put("tools", toolsJson);
                requestBody.put("tool_choice", "auto");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choice = root.path("choices").get(0);
            JsonNode message = choice.path("message");

            String content = message.path("content").asText(null);
            List<LLMToolCall> parsedCalls = new ArrayList<>();

            if (message.has("tool_calls")) {
                for (JsonNode tc : message.path("tool_calls")) {
                    String id = tc.path("id").asText();
                    String fnName = tc.path("function").path("name").asText();
                    String argsStr = tc.path("function").path("arguments").asText("{}");
                    Map<String, Object> args = objectMapper.readValue(argsStr, new TypeReference<Map<String, Object>>() {});
                    parsedCalls.add(LLMToolCall.builder().id(id).name(fnName).arguments(args).build());
                }
            }

            return LLMResponse.builder()
                    .content(content)
                    .toolCalls(parsedCalls)
                    .finishReason(choice.path("finish_reason").asText())
                    .build();

        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            throw new RuntimeException("LLM execution error: " + e.getMessage(), e);
        }
    }
}
