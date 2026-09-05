package com.kiranapilot.agent.llm;

import com.kiranapilot.agent.ToolDefinition;

import java.util.List;

public interface LLMClient {
    LLMResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools);
}
