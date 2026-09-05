package com.kiranapilot.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolResult {

    public enum Status {
        SUCCESS,
        ERROR
    }

    private Status status;
    private Object data;
    private String message;

    private byte[] documentAttachment;
    private String attachmentFilename;
    private String attachmentMimeType;

    public static ToolResult success(Object data, String message) {
        return ToolResult.builder()
                .status(Status.SUCCESS)
                .data(data)
                .message(message)
                .build();
    }

    public static ToolResult error(String errorMessage) {
        return ToolResult.builder()
                .status(Status.ERROR)
                .message(errorMessage)
                .data(errorMessage)
                .build();
    }

    public static ToolResult withAttachment(Object data, String message, byte[] attachment, String filename, String mimeType) {
        return ToolResult.builder()
                .status(Status.SUCCESS)
                .data(data)
                .message(message)
                .documentAttachment(attachment)
                .attachmentFilename(filename)
                .attachmentMimeType(mimeType)
                .build();
    }
}
