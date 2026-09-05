package com.kiranapilot.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    private Role role;
    private String content;
    private List<LLMToolCall> toolCalls;
    private String toolCallId;
    private String name;

    public static ChatMessage system(String text) {
        return ChatMessage.builder().role(Role.SYSTEM).content(text).build();
    }

    public static ChatMessage user(String text) {
        return ChatMessage.builder().role(Role.USER).content(text).build();
    }

    public static ChatMessage assistant(String text) {
        return ChatMessage.builder().role(Role.ASSISTANT).content(text).build();
    }

    public static ChatMessage assistantWithTools(String text, List<LLMToolCall> toolCalls) {
        return ChatMessage.builder().role(Role.ASSISTANT).content(text).toolCalls(toolCalls).build();
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        return ChatMessage.builder().role(Role.TOOL).toolCallId(toolCallId).name(name).content(content).build();
    }
}
