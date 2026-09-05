package com.kiranapilot.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LLMClientFactory {

    private final ObjectMapper objectMapper;

    @Value("${kiranapilot.ai.provider:mock}")
    private String provider;

    @Value("${kiranapilot.ai.model:gemini-1.5-flash}")
    private String model;

    @Value("${kiranapilot.ai.api-key:}")
    private String apiKey;

    @Bean
    public LLMClient llmClient() {
        log.info("Configuring LLM Client for provider: '{}', model: '{}'", provider, model);

        if ("gemini".equalsIgnoreCase(provider) && apiKey != null && !apiKey.trim().isEmpty()) {
            return new GeminiLLMClient(apiKey.trim(), model, objectMapper);
        } else if ("openai".equalsIgnoreCase(provider) && apiKey != null && !apiKey.trim().isEmpty()) {
            return new OpenAILLMClient(apiKey.trim(), model, null, objectMapper);
        } else if ("anthropic".equalsIgnoreCase(provider) && apiKey != null && !apiKey.trim().isEmpty()) {
            return new AnthropicLLMClient(apiKey.trim(), model, objectMapper);
        } else {
            log.info("No external LLM API key provided or provider='mock'. Defaulting to intelligent MockLLMClient.");
            return new MockLLMClient();
        }
    }
}
