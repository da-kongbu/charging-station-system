package com.charging.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.charging.dto.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 大模型对话服务
 * 
 * 封装与硅基流动 (SiliconFlow) / DeepSeek 大模型 API 的所有通信逻辑。
 * 支持三种对话模式：
 * 1. 单轮对话 (chat)
 * 2. 多轮上下文记忆对话 (chatWithHistory)
 * 3. SSE 流式打字机对话 (chatStream)
 */
@Slf4j
@Service
public class AiChatService {

    @Value("${ai.siliconflow.api-key}")
    private String apiKey;

    @Value("${ai.siliconflow.url}")
    private String apiUrl;

    @Value("${ai.siliconflow.model}")
    private String modelName;

    @Autowired(required = false)
    private RagService ragService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 系统提示词（AI 的人设）
     */
    private static final String SYSTEM_PROMPT = """
            你是"智充助手"，一个专业的共享充电桩智能客服。你的职责是：
            1. 回答用户关于充电桩使用方法、计费规则、故障排除的问题。
            2. 如果系统提供了【参考知识】，请优先基于这些知识回答，不要编造信息。
            3. 如果问题超出你的知识范围，请诚实告知用户并建议联系人工客服。
            4. 回答要简洁、专业、友好。
            """;

    // ==================== 模式一：单轮对话（带 RAG 增强） ====================

    /**
     * 单轮对话：用户问一句，AI 答一句。
     * 会自动触发 RAG 检索，将相关知识注入 Prompt。
     */
    public String chat(String userQuestion) {
        // 1. 使用 RAG 检索相关知识
        String ragContext = retrieveKnowledge(userQuestion);

        // 2. 组装系统提示词（如果有知识库命中，拼接进去）
        String systemPromptWithRag = SYSTEM_PROMPT;
        if (!ragContext.isEmpty()) {
            systemPromptWithRag += "\n\n【参考知识（请优先基于以下内容回答）】：\n" + ragContext;
        }

        // 3. 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPromptWithRag));
        messages.add(Map.of("role", "user", "content", userQuestion));

        // 4. 调用大模型
        return callLlm(messages, false);
    }

    // ==================== 模式二：多轮对话（带上下文记忆） ====================

    /**
     * 多轮对话：前端携带完整的历史聊天记录发过来，AI 基于上下文回答。
     */
    public String chatWithHistory(List<ChatRequest.Message> historyMessages) {
        // 1. 取最后一条用户消息做 RAG 检索
        String lastUserMessage = "";
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            if ("user".equals(historyMessages.get(i).getRole())) {
                lastUserMessage = historyMessages.get(i).getContent();
                break;
            }
        }
        String ragContext = retrieveKnowledge(lastUserMessage);

        // 2. 组装完整消息列表
        List<Map<String, String>> messages = new ArrayList<>();

        String systemPromptWithRag = SYSTEM_PROMPT;
        if (!ragContext.isEmpty()) {
            systemPromptWithRag += "\n\n【参考知识】：\n" + ragContext;
        }
        messages.add(Map.of("role", "system", "content", systemPromptWithRag));

        // 将历史消息追加
        for (ChatRequest.Message msg : historyMessages) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        return callLlm(messages, false);
    }

    // ==================== 模式三：SSE 流式打字机 ====================

    /**
     * SSE 流式对话：大模型逐 Token 返回，前端实现类似 ChatGPT 的打字机效果。
     */
    public void chatStream(String userQuestion, SseEmitter emitter) {
        new Thread(() -> {
            try {
                // 1. RAG 检索
                String ragContext = retrieveKnowledge(userQuestion);
                String systemPromptWithRag = SYSTEM_PROMPT;
                if (!ragContext.isEmpty()) {
                    systemPromptWithRag += "\n\n【参考知识】：\n" + ragContext;
                }

                // 2. 构建请求
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPromptWithRag));
                messages.add(Map.of("role", "user", "content", userQuestion));

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", modelName);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.7);
                requestBody.put("stream", true); // 关键：开启流式模式

                String jsonBody = JSON.toJSONString(requestBody);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                // 3. 以流式方式接收响应
                HttpResponse<java.io.InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    emitter.send(SseEmitter.event().data("大模型请求失败，状态码: " + response.statusCode()));
                    emitter.complete();
                    return;
                }

                // 4. 逐行读取 SSE 数据流
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                // 大模型输出完毕
                                break;
                            }
                            try {
                                JSONObject chunk = JSON.parseObject(data);
                                String content = chunk.getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("delta")
                                        .getString("content");
                                if (content != null && !content.isEmpty()) {
                                    // 将每个 Token 实时推送给前端
                                    emitter.send(SseEmitter.event().data(content));
                                }
                            } catch (Exception parseEx) {
                                // 解析某一行失败时跳过继续
                                log.warn("解析流式数据失败: {}", line);
                            }
                        }
                    }
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 流式对话异常", e);
                try {
                    emitter.send(SseEmitter.event().data("服务器内部错误: " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        }).start();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 调用大模型 API（非流式）
     */
    private String callLlm(List<Map<String, String>> messages, boolean stream) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("stream", stream);

            String jsonBody = JSON.toJSONString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("大模型 API 调用失败，状态码: {}，响应: {}", response.statusCode(), response.body());
                return "抱歉，智充助手暂时无法响应，请稍后再试。(错误码: " + response.statusCode() + ")";
            }

            JSONObject responseJson = JSON.parseObject(response.body());
            return responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (Exception e) {
            log.error("调用大模型异常", e);
            return "抱歉，智充助手暂时无法响应: " + e.getMessage();
        }
    }

    /**
     * 调用 RAG 知识库检索相关知识片段
     */
    private String retrieveKnowledge(String userQuestion) {
        if (ragService == null || userQuestion == null || userQuestion.isBlank()) {
            return "";
        }
        try {
            List<String> results = ragService.search(userQuestion, 3);
            if (results.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                sb.append("知识片段").append(i + 1).append("：").append(results.get(i)).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为无知识库模式", e);
            return "";
        }
    }
}
