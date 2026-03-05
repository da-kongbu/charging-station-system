package com.charging.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.charging.dto.ChargingStationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * AI 智能体 (Agent) 服务
 *
 * 核心原理：Function Calling（函数调用 / 工具调用）
 *
 * 传统 AI 聊天：用户问 → AI 凭空编答案
 * Agent 模式：用户问 → AI 判断需要查什么 → 调用我们定义的工具函数 → 拿到真实数据 → 用人话总结回复
 *
 * 工作流程：
 * 1. 把用户问题 + 工具定义（tools）一起发给大模型
 * 2. 大模型返回 tool_calls（"我想调用 xxx 函数"）
 * 3. 我们在 Java 里执行该函数，拿到真实数据
 * 4. 把函数执行结果发回给大模型
 * 5. 大模型基于真实数据，用自然语言给用户一个精准的回答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    @Value("${ai.siliconflow.api-key}")
    private String apiKey;

    @Value("${ai.siliconflow.url}")
    private String apiUrl;

    @Value("${ai.siliconflow.model}")
    private String modelName;

    /**
     * 直接复用已有的 ChargingStationService
     * 它内部的 convertToDTO() 已经统计了每个站的 pileCount 和 availablePileCount
     */
    private final ChargingStationService stationService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 用户当前位置（每次请求从前端传入）
     * 使用 ThreadLocal 保证线程安全
     */
    private final ThreadLocal<Double> currentLat = new ThreadLocal<>();
    private final ThreadLocal<Double> currentLng = new ThreadLocal<>();

    /**
     * 系统提示词（Agent 人设）
     */
    private static final String AGENT_SYSTEM_PROMPT = """
            你是"智充助手"，一个拥有实时数据查询能力的共享充电桩 AI 智能体。
            你可以通过调用工具函数，查询系统中的真实充电站和车位数据。

            行为准则：
            1. 当用户询问充电站信息时，必须调用工具获取实时数据，不要编造。
            2. 数据中包含"总桩数"和"当前可用桩数"，请务必主动告知用户。
            3. 数据已按照距离从近到远排序，包含了"距离(km)"字段，请按此顺序展示给用户。
            4. 返回数据后，用友好、简洁的自然语言总结给用户，优先推荐距离近且有空闲桩的站点。
            5. 如果没有找到数据，诚实告知。
            6. 主动引导用户进行下一步操作（如预约）。
            """;

    /**
     * Agent 智能体入口：接收用户问题和位置信息，自动决策是否需要调用工具
     */
    public String agentChat(String userQuestion, Double lat, Double lng) {
        try {
            // 把用户位置存入 ThreadLocal，工具函数执行时可以取到
            currentLat.set(lat);
            currentLng.set(lng);

            // 1. 第一轮：带工具定义发给大模型
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", AGENT_SYSTEM_PROMPT));
            messages.add(Map.of("role", "user", "content", userQuestion));

            JSONObject firstResponse = callLlmWithTools(messages);

            if (firstResponse == null) {
                return "抱歉，智充助手暂时无法响应。";
            }

            JSONObject choice = firstResponse.getJSONArray("choices").getJSONObject(0);
            JSONObject assistantMessage = choice.getJSONObject("message");
            String finishReason = choice.getString("finish_reason");

            // 2. 检查大模型是否想调用工具
            if ("tool_calls".equals(finishReason) && assistantMessage.containsKey("tool_calls")) {
                JSONArray toolCalls = assistantMessage.getJSONArray("tool_calls");

                // 把 assistant 的工具调用消息追加到历史
                @SuppressWarnings("unchecked")
                Map<String, Object> assistantMap = assistantMessage.toJavaObject(Map.class);
                messages.add(assistantMap);

                // 3. 逐个执行工具调用
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject toolCall = toolCalls.getJSONObject(i);
                    String toolCallId = toolCall.getString("id");
                    String functionName = toolCall.getJSONObject("function").getString("name");
                    String arguments = toolCall.getJSONObject("function").getString("arguments");

                    log.info("Agent 调用工具: {} | 参数: {}", functionName, arguments);

                    String functionResult = executeFunction(functionName, arguments);

                    Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                    toolResultMsg.put("role", "tool");
                    toolResultMsg.put("tool_call_id", toolCallId);
                    toolResultMsg.put("content", functionResult);
                    messages.add(toolResultMsg);
                }

                // 4. 第二轮：把工具执行结果发回大模型，让它生成最终的自然语言回答
                JSONObject secondResponse = callLlmRaw(messages);
                if (secondResponse != null) {
                    return secondResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }
            }

            String content = assistantMessage.getString("content");
            return content != null ? content : "抱歉，我暂时无法理解您的问题。";

        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            return "抱歉，智充助手遇到了一些问题: " + e.getMessage();
        } finally {
            // 清理 ThreadLocal 防止内存泄漏
            currentLat.remove();
            currentLng.remove();
        }
    }

    // ==================== 工具执行引擎 ====================

    private String executeFunction(String functionName, String arguments) {
        try {
            JSONObject args = JSON.parseObject(arguments);

            return switch (functionName) {
                case "query_all_stations" -> queryAllStations();
                case "query_station_by_keyword" -> queryStationByKeyword(args.getString("keyword"));
                case "query_station_detail" -> queryStationDetail(args.getLong("station_id"));
                default -> "未知工具: " + functionName;
            };
        } catch (Exception e) {
            log.error("工具执行失败: {}", functionName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    // ==================== 工具函数实现 ====================

    /**
     * 工具1：查询所有可用充电站（含实时可用桩数 + 按距离排序）
     */
    private String queryAllStations() {
        List<ChargingStationDTO> stations = stationService.findAllAvailable();
        if (stations.isEmpty())
            return "当前没有可用的充电站";

        return stationsToJson(stations);
    }

    /**
     * 工具2：按关键词搜索充电站
     */
    private String queryStationByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank())
            return "请提供搜索关键词";

        List<ChargingStationDTO> stations = stationService.searchByKeyword(keyword);
        if (stations.isEmpty())
            return "没有找到包含 '" + keyword + "' 的充电站";

        return stationsToJson(stations);
    }

    /**
     * 工具3：查询指定充电站详情（含每个桩的详细状态）
     */
    private String queryStationDetail(Long stationId) {
        if (stationId == null)
            return "请提供充电站ID";

        Optional<ChargingStationDTO> stationOpt = stationService.findById(stationId);
        if (stationOpt.isEmpty())
            return "充电站ID " + stationId + " 不存在";

        ChargingStationDTO station = stationOpt.get();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("名称", station.getName());
        result.put("地址", station.getAddress());
        result.put("营业时间", station.getBusinessHours());
        result.put("联系电话", station.getContact());
        result.put("总桩数", station.getPileCount());
        result.put("当前可用桩数", station.getAvailablePileCount());

        if (station.getPiles() != null) {
            List<Map<String, Object>> pileList = new ArrayList<>();
            for (var pile : station.getPiles()) {
                Map<String, Object> pileInfo = new LinkedHashMap<>();
                pileInfo.put("桩编号", pile.getPileCode());
                pileInfo.put("类型", pile.getPileType());
                pileInfo.put("功率", pile.getPower() + "kW");
                pileInfo.put("状态", pile.getStatus() == 1 ? "空闲可用" : pile.getStatus() == 2 ? "充电中" : "离线");
                pileList.add(pileInfo);
            }
            result.put("充电桩详情", pileList);
        }

        return JSON.toJSONString(result);
    }

    // ==================== 距离计算 & 排序 ====================

    /**
     * 将站点列表转为 JSON，支持按距离排序
     * 复用了前端 useLocation.js 中的 Haversine 公式，在 Java 中实现
     */
    private String stationsToJson(List<ChargingStationDTO> stations) {
        Double userLat = currentLat.get();
        Double userLng = currentLng.get();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChargingStationDTO s : stations) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", s.getId());
            info.put("名称", s.getName());
            info.put("地址", s.getAddress());
            info.put("城市", s.getCity());
            info.put("总桩数", s.getPileCount());
            info.put("当前可用桩数", s.getAvailablePileCount());
            info.put("营业时间", s.getBusinessHours() != null ? s.getBusinessHours() : "全天");

            // 如果有用户位置 + 站点有经纬度，计算距离
            if (userLat != null && userLng != null
                    && s.getLatitude() != null && s.getLongitude() != null) {
                double dist = haversineDistance(
                        userLat, userLng,
                        s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
                info.put("距离(km)", Math.round(dist * 10.0) / 10.0); // 保留一位小数
            }

            result.add(info);
        }

        // 按距离排序（有距离的排前面，没有距离的排后面）
        if (userLat != null && userLng != null) {
            result.sort((a, b) -> {
                Object da = a.get("距离(km)");
                Object db = b.get("距离(km)");
                if (da == null && db == null)
                    return 0;
                if (da == null)
                    return 1;
                if (db == null)
                    return -1;
                return Double.compare(((Number) da).doubleValue(), ((Number) db).doubleValue());
            });
        }

        return JSON.toJSONString(result);
    }

    /**
     * Haversine 公式：计算地球上两点的球面距离（km）
     * 与前端 useLocation.js 中的 calculateDistance 算法完全一致
     */
    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6378.137; // 地球半径（千米）

        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);

        double s = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin(a / 2), 2) +
                        Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));

        return s * EARTH_RADIUS;
    }

    // ==================== LLM 调用方法 ====================

    private JSONObject callLlmWithTools(List<Map<String, Object>> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);
        requestBody.put("tools", buildToolDefinitions());
        requestBody.put("tool_choice", "auto");
        return sendRequest(requestBody);
    }

    private JSONObject callLlmRaw(List<Map<String, Object>> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.5);
        return sendRequest(requestBody);
    }

    private JSONObject sendRequest(Map<String, Object> requestBody) {
        try {
            String jsonBody = JSON.toJSONString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("大模型 API 调用失败: {}", response.body());
                return null;
            }

            return JSON.parseObject(response.body());
        } catch (Exception e) {
            log.error("大模型 API 请求异常", e);
            return null;
        }
    }

    // ==================== 工具定义（OpenAI 格式） ====================

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(buildTool(
                "query_all_stations",
                "查询系统中所有正在营业的充电站列表，按距离从近到远排序，包括名称、地址、距离、总桩数和当前可用空闲桩数",
                Map.of("type", "object", "properties", Map.of(), "required", List.of())));

        tools.add(buildTool(
                "query_station_by_keyword",
                "按关键词搜索充电站，可以搜索充电站名称、地址或城市",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of("type", "string", "description", "搜索关键词")),
                        "required", List.of("keyword"))));

        tools.add(buildTool(
                "query_station_detail",
                "查询指定充电站的详细信息，包括每根充电桩的编号、类型、功率和当前状态",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "station_id", Map.of("type", "integer", "description", "充电站的ID编号")),
                        "required", List.of("station_id"))));

        return tools;
    }

    private Map<String, Object> buildTool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", Map.of(
                "name", name,
                "description", description,
                "parameters", parameters));
        return tool;
    }
}
