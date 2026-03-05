package com.charging.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.charging.dto.KnowledgeChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）服务
 *
 * 核心职责：
 * 1. 项目启动时，从 resources/knowledge/ 目录加载知识文档
 * 2. 将文档切块并调用 Embedding API 转换为向量
 * 3. 当用户提问时，将问题也转换为向量，通过余弦相似度检索最匹配的知识块
 * 4. 将检索结果注入 Prompt，让大模型基于真实知识回答，避免"幻觉"
 */
@Slf4j
@Service
public class RagService {

    @Value("${ai.siliconflow.api-key}")
    private String apiKey;

    @Value("${ai.siliconflow.embedding-url}")
    private String embeddingUrl;

    @Value("${ai.siliconflow.embedding-model}")
    private String embeddingModel;

    /**
     * 内存知识库：存储所有知识块及其向量
     */
    private final List<KnowledgeChunk> knowledgeBase = new ArrayList<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 项目启动时自动加载知识库
     */
    @PostConstruct
    public void init() {
        log.info("========== 正在初始化 RAG 知识库 ==========");
        try {
            loadAndIndexDocument("knowledge/charging_guide.txt");
            loadAndIndexDocument("knowledge/billing_rules.txt");
            log.info("========== RAG 知识库初始化完成，共加载 {} 个知识块 ==========", knowledgeBase.size());
        } catch (Exception e) {
            log.error("RAG 知识库初始化失败，系统将降级为无知识库模式", e);
        }
    }

    /**
     * 检索与用户问题最相关的 Top-K 知识块
     *
     * @param query 用户的提问文本
     * @param topK  返回最相关的前 K 条结果
     * @return 匹配的知识文本列表
     */
    public List<String> search(String query, int topK) {
        if (knowledgeBase.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 1. 将用户问题转为向量
            float[] queryEmbedding = getEmbedding(query);
            if (queryEmbedding == null) {
                return Collections.emptyList();
            }

            // 2. 计算与每个知识块的余弦相似度，找出最相关的 Top-K
            return knowledgeBase.stream()
                    .map(chunk -> new AbstractMap.SimpleEntry<>(chunk.getText(),
                            cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())) // 降序排列
                    .limit(topK)
                    .filter(entry -> entry.getValue() > 0.3) // 相似度阈值过滤，太低的就丢弃
                    .map(AbstractMap.SimpleEntry::getKey)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("RAG 检索异常", e);
            return Collections.emptyList();
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 加载一份知识文档，切块后做 Embedding 索引
     */
    private void loadAndIndexDocument(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("知识文档不存在，跳过: {}", resourcePath);
                return;
            }

            // 读取全文
            String fullText;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                fullText = reader.lines().collect(Collectors.joining("\n"));
            }

            // 按段落切块（以空行为分隔符）
            String[] paragraphs = fullText.split("\n\n+");
            log.info("文档 [{}] 切分为 {} 个段落", resourcePath, paragraphs.length);

            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (trimmed.length() < 10) {
                    continue; // 跳过太短的段落
                }
                float[] embedding = getEmbedding(trimmed);
                if (embedding != null) {
                    knowledgeBase.add(new KnowledgeChunk(trimmed, embedding));
                }
            }
        } catch (Exception e) {
            log.error("加载知识文档失败: {}", resourcePath, e);
        }
    }

    /**
     * 调用硅基流动 Embedding API，将文本转换为向量
     */
    private float[] getEmbedding(String text) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            String jsonBody = JSON.toJSONString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Embedding API 调用失败: {}", response.body());
                return null;
            }

            JSONObject responseJson = JSON.parseObject(response.body());
            JSONArray embeddingArray = responseJson
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONArray("embedding");

            float[] result = new float[embeddingArray.size()];
            for (int i = 0; i < embeddingArray.size(); i++) {
                result[i] = embeddingArray.getFloatValue(i);
            }
            return result;

        } catch (Exception e) {
            log.error("获取 Embedding 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算两个向量之间的余弦相似度
     * 值域范围：[-1, 1]，值越大表示越相似
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length)
            return 0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }
}
