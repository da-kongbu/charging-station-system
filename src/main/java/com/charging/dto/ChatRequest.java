package com.charging.dto;

import lombok.Data;
import java.util.List;

/**
 * 多轮对话请求 DTO
 * 前端将完整的历史聊天记录（含 role 和 content）一起发给后端，实现上下文记忆
 */
@Data
public class ChatRequest {

    /**
     * 消息列表，包含所有历史对话
     */
    private List<Message> messages;

    @Data
    public static class Message {
        /**
         * 角色：user（用户）或 assistant（AI 回复）
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;
    }
}
