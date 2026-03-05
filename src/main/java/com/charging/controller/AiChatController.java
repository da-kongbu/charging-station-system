package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.dto.ChatRequest;
import com.charging.service.AgentService;
import com.charging.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能助手控制器
 *
 * 暴露四个核心接口：
 * 1. GET /api/ai/chat?question=xxx → 单轮对话（带 RAG 知识增强）
 * 2. POST /api/ai/conversation → 多轮对话（携带历史上下文）
 * 3. GET /api/ai/stream?question=xxx → SSE 流式打字机对话
 * 4. GET /api/ai/agent?question=xxx → Agent 智能体（带工具调用能力）
 */
@RestController
@RequestMapping(value = "/api/ai", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final AgentService agentService;

    /**
     * 单轮对话接口
     * 浏览器直接访问即可测试：http://localhost:8080/api/ai/chat?question=充电桩怎么使用
     */
    @GetMapping("/chat")
    public ResponseEntity<ApiResponse<String>> chat(@RequestParam String question) {
        String answer = aiChatService.chat(question);
        return ResponseEntity.ok(ApiResponse.success("AI 回复成功", answer));
    }

    /**
     * 多轮对话接口
     * 前端携带完整的聊天历史记录（JSON 数组）发送 POST 请求
     */
    @PostMapping("/conversation")
    public ResponseEntity<ApiResponse<String>> conversation(@RequestBody ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("消息列表不能为空"));
        }
        String answer = aiChatService.chatWithHistory(request.getMessages());
        return ResponseEntity.ok(ApiResponse.success("AI 回复成功", answer));
    }

    /**
     * SSE 流式对话接口
     * 实现类似 ChatGPT 的逐字蹦出效果
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestParam String question) {
        SseEmitter emitter = new SseEmitter(120_000L);
        aiChatService.chatStream(question, emitter);
        return emitter;
    }

    /**
     * Agent 智能体接口（Function Calling）
     * AI 拥有调用工具的能力，可以查询真实的充电站和车位数据
     *
     * 参数 lat/lng 由前端浏览器 Geolocation API 获取，用于距离排序
     * 测试：http://localhost:8080/api/ai/agent?question=附近充电站&lat=31.77&lng=119.95
     */
    @GetMapping("/agent")
    public ResponseEntity<ApiResponse<String>> agent(
            @RequestParam String question,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        String answer = agentService.agentChat(question, lat, lng);
        return ResponseEntity.ok(ApiResponse.success("Agent 回复成功", answer));
    }
}
