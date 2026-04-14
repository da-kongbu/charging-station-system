package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {

    private String type;
    private String content;
    private Object data;

    public static AgentChatResponse text(String content) {
        return AgentChatResponse.builder()
                .type("text")
                .content(content)
                .build();
    }

    public static AgentChatResponse of(String type, String content, Object data) {
        return AgentChatResponse.builder()
                .type(type)
                .content(content)
                .data(data)
                .build();
    }
}
