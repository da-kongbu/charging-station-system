package com.charging.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.charging.dto.ChargingStationDTO;
import com.charging.dto.ReservationDTO;
import com.charging.dto.ReservationRequest;
import com.charging.dto.agent.AgentChatRequest;
import com.charging.dto.agent.AgentChatResponse;
import com.charging.dto.agent.AgentHistoryMessage;
import com.charging.dto.agent.AvailableSpotCardData;
import com.charging.dto.agent.QueryAvailableSpotsArgs;
import com.charging.dto.agent.QueryStationByKeywordArgs;
import com.charging.dto.agent.StationCardData;
import com.charging.dto.agent.StationDetailCardData;
import com.charging.dto.agent.UserReservationCardData;
import com.charging.dto.agent.RecommendBookingCardData;
import com.charging.entity.ParkingSpot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

/**
 * AI 智能体 (Agent) 服务
 *
 * 核心原理：Function Calling（函数调用 / 工具调用）
 *
 * 工作流程：
 * 1. 把用户问题 + 工具定义（tools）一起发给大模型
 * 2. 大模型返回 tool_calls（"我想调用 xxx 函数"）
 * 3. 我们在 Java 里执行该函数，拿到真实数据
 * 4. 把函数执行结果发回给大模型
 * 5. 大模型基于真实数据，用自然语言给用户一个精准的回答
 * 6. 后端根据调用了哪些 tool，智能决定附加富卡片数据
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

    @Value("${ai.siliconflow.agent-timeout-seconds:150}")
    private long agentTimeoutSeconds;

    private final ChargingStationService stationService;
    private final ReservationService reservationService;
    private final ParkingSpotService parkingSpotService;
    private final StationDiscoveryService stationDiscoveryService;

    @Autowired(required = false)
    private RagService ragService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ThreadLocal<Double> currentLat = new ThreadLocal<>();
    private final ThreadLocal<Double> currentLng = new ThreadLocal<>();
    private final ThreadLocal<Long> currentUserId = new ThreadLocal<>();
    private final ThreadLocal<String> currentRequestId = new ThreadLocal<>();
    private final ThreadLocal<String> currentRouteType = new ThreadLocal<>();
    private final ThreadLocal<Integer> currentHistoryCount = new ThreadLocal<>();
    private final ThreadLocal<String> currentQuestionSummary = new ThreadLocal<>();
    private final ThreadLocal<LlmFailureType> currentLlmFailureType = new ThreadLocal<>();
    private final Clock clock = Clock.systemDefaultZone();
    private final ConcurrentHashMap<String, PendingReservation> pendingReservations = new ConcurrentHashMap<>();

    private static final DateTimeFormatter SYSTEM_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Pattern STATION_LIST_ITEM_PATTERN = Pattern.compile("(\\d+)\\.\\s+id=(\\d+)\\s+(.*?)\\s+可用桩");
    private static final Pattern STATION_DETAIL_PATTERN = Pattern.compile("\\[站点详情]\\s+id=(\\d+)\\s+(.*?)\\s+可用桩");
    private static final Pattern AVAILABLE_SPOT_LIST_ITEM_PATTERN = Pattern.compile("(\\d+)\\.\\s+spot_id=(\\d+)\\s+(\\S+)");
    private static final Pattern LOCKED_STATION_ID_PATTERN = Pattern.compile("站点id为\\s*(\\d+)");
    private static final Pattern PENDING_CONFIRM_TOKEN_PATTERN = Pattern.compile("confirm_token=([^\\s]+)");
    private static final Pattern PENDING_STATION_ID_PATTERN = Pattern.compile("station_id=(\\d+)");
    private static final Pattern PENDING_SPOT_ID_PATTERN = Pattern.compile("spot_id=(\\d+)");
    private static final Pattern PENDING_CHARGING_TYPE_PATTERN = Pattern.compile("charging_type=([^\\s]+)");
    private static final Duration PENDING_RESERVATION_TTL = Duration.ofMinutes(15);
    private static final String DEFAULT_RESERVATION_CHARGING_TYPE = "AC";

    private static final String AGENT_SYSTEM_PROMPT = """
            你是"智充助手"，一个拥有实时数据查询能力和领域知识库的共享充电桩 AI 智能体。
            你可以通过调用工具函数，查询系统中的真实充电站和车位数据，并基于查询结果为用户推荐预约方案。
            你拥有多轮对话记忆，可以理解上下文中的指代（如"第一个"、"它"、"那个"）。

            【最重要的规则】
            **当用户首次询问充电站信息时，必须调用工具获取最新数据。但当用户基于上一轮的查询结果进行后续操作（如"预约第一个"、"帮我预约那个"）时，直接使用对话历史中的站点ID，不要重复查询站点列表，直接调用 query_available_spots。**

            行为准则：
            1. 当用户首次询问充电站信息时，调用 query_all_stations 或 query_station_by_keyword 获取实时数据。
            2. 返回数据后，用简洁的自然语言总结。**不要逐条重复站点全名**（卡片已展示），只需概括距离和可用状态，例如"约 5.0 km，有空闲桩，建议优先查看"。
            3. 如果没有找到数据，诚实告知。
            4. 当用户指定了具体充电站名称或品牌（如"国家电网"、"特来电"），必须用 query_station_by_keyword 按该关键词搜索。
            5. **上下文理解**：当用户说"第一个"、"帮我预约刚才那个"时，回顾对话历史中你返回的充电站列表，用对应的站点 ID 操作。绝不能凭空编造站点。
            6. 当用户想预约时：如果只针对一个明确站点查车位，用 query_available_spots 查车位 → 调用 recommend_booking 生成推荐方案。若用户要求快充/慢充且表达了“没有就换下一个站”“顺延推荐”“找别的站”等含义，必须改用 query_available_spots_across_stations，按推荐顺序从指定站点继续查到后续站点，再对找到的第一个车位调用 recommend_booking。若顺延后推荐的是其他站点，要先明确说明“原站点暂无符合条件车位”，再展示 AI 推荐方案，不要把用户未确认的新站点直接当成已确认预约。若用户没有明确说明快充/慢充，默认按慢充处理，不要继续追问类型。系统会引导用户到预约确认页面完成预约。你不需要也不能直接创建预约，只需推荐即可。
            7. 如果用户未登录就尝试预约，请提示"请先登录后再进行预约操作"。
            8. **回复风格**：简洁、有层次。推荐站点时，先给一句总数总结，再逐项给出推荐理由（距离、空闲桩、适合优先/备选），最后加一句引导（如"点击下方卡片查看详情"）。不要写长段落。
            9. 当用户询问充电桩使用方法、计费规则、故障排除等常识性问题时，如果系统提供了【参考知识】，请优先基于这些知识回答，不要编造信息。
            10. 系统会提供当前时间。用户说"现在"、"今天"、"明天下午3点"、"持续两小时"这类相对时间时，你要先换算成明确的 ISO 时间，再调用工具，不要因为这些表达方式继续追问。
            11. 用户一次给出多个条件时要一起处理，例如"预约第一个、现在开始两小时、要快充、如果没有快充就换一个有快充的站"。你应优先使用上下文中的站点 ID 和现有条件直接调用工具；当前站点不满足时，继续尝试最近对话里已有的其他候选站点，不要把用户已经给出的条件再问一遍。
            """;

    /**
     * Agent 智能体入口：接收用户问题和位置信息，自动决策是否需要调用工具
     *
     * @param userQuestion 用户问题
     * @param lat          用户纬度
     * @param lng          用户经度
     * @param userId       当前用户ID（可为null，未登录状态）
     * @return 结构化响应 { type, content, data? }
     */
    public AgentChatResponse agentChat(AgentChatRequest request, Long userId, String requestId) {
        String safeRequestId = requestId != null && !requestId.isBlank()
                ? requestId
                : UUID.randomUUID().toString().replace("-", "");
        try {
            currentLat.set(request.getLat());
            currentLng.set(request.getLng());
            currentUserId.set(userId);
            currentRequestId.set(safeRequestId);
            currentHistoryCount.set(request.getHistory() != null ? request.getHistory().size() : 0);
            currentQuestionSummary.set(summarizeQuestion(request.getQuestion()));
            currentLlmFailureType.set(LlmFailureType.NONE);

            SimpleStationQuery simpleRoute = resolveSimpleStationQuery(request.getQuestion());
            if (simpleRoute.matched()) {
                currentRouteType.set("simple_station");
                log.info("simple_route_hit | requestId={} | keyword={} | chargingType={}",
                        safeRequestId, simpleRoute.keyword(), simpleRoute.chargingType());
                return stationDiscoveryService.buildStationCardResponse(
                        request.getLat(), request.getLng(), simpleRoute.keyword(), simpleRoute.chargingType(), 3);
            }

            PreparsedReservationIntent reservationIntent = resolvePreparsedReservationIntent(
                    request.getQuestion(), request.getHistory());
            if (reservationIntent.matched()) {
                currentRouteType.set("local_reservation");
                log.info("reservation_route_hit | requestId={} | stationId={} | stationName={} | startTime={} | endTime={} | chargingType={}",
                        safeRequestId,
                        reservationIntent.stationId(),
                        reservationIntent.stationName(),
                        reservationIntent.startTime(),
                        reservationIntent.endTime(),
                        reservationIntent.chargingType());
                return executeDeterministicReservation(reservationIntent);
            }

            PreparsedStationDetailIntent stationDetailIntent = resolvePreparsedStationDetailIntent(
                    request.getQuestion(), request.getHistory());
            if (stationDetailIntent.matched()) {
                currentRouteType.set("local_station_detail");
                log.info("station_detail_route_hit | requestId={} | stationId={} | stationName={}",
                        safeRequestId,
                        stationDetailIntent.stationId(),
                        stationDetailIntent.stationName());
                return executeDeterministicStationDetail(stationDetailIntent);
            }

            currentRouteType.set("agent");
            log.info("agent_route | requestId={} | historyCount={} | question={}",
                    safeRequestId,
                    currentHistoryCount.get(),
                    currentQuestionSummary.get());
            return standardAgentChat(request.getQuestion(), request.getHistory());

        } catch (Exception e) {
            log.error("agent_request_failed | requestId={} | route={} | question={}",
                    safeRequestId, currentRouteType.get(), currentQuestionSummary.get(), e);
            return AgentChatResponse.text("抱歉，智充助手遇到了一些问题，请稍后再试。");
        } finally {
            currentLat.remove();
            currentLng.remove();
            currentUserId.remove();
            currentRequestId.remove();
            currentRouteType.remove();
            currentHistoryCount.remove();
            currentQuestionSummary.remove();
            currentLlmFailureType.remove();
        }
    }

    public AgentChatResponse agentChat(String userQuestion, Double lat, Double lng, Long userId,
                                       List<Map<String, String>> history) {
        List<AgentHistoryMessage> historyMessages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> item : history) {
                historyMessages.add(AgentHistoryMessage.builder()
                        .role(item.get("role"))
                        .content(item.get("content"))
                        .build());
            }
        }
        return agentChat(AgentChatRequest.builder()
                .question(userQuestion)
                .lat(lat)
                .lng(lng)
                .history(historyMessages)
                .build(), userId, UUID.randomUUID().toString().replace("-", ""));
    }

    public String confirmPendingReservation(String confirmToken, Long userId, String requestId) {
        String safeRequestId = requestId != null && !requestId.isBlank()
                ? requestId
                : UUID.randomUUID().toString().replace("-", "");
        try {
            currentUserId.set(userId);
            currentRequestId.set(safeRequestId);
            cleanupExpiredPendingReservations();
            return confirmReservation(confirmToken);
        } finally {
            currentUserId.remove();
            currentRequestId.remove();
        }
    }

    // ==================== Agentic Loop ====================

    private AgentChatResponse standardAgentChat(String userQuestion, List<AgentHistoryMessage> history) {
        List<ToolRecord> toolRecords = new ArrayList<>();

        // RAG 检索：将知识库相关内容注入 Agent 的 System Prompt
        String systemPrompt = buildSystemPrompt();
        String ragContext = retrieveKnowledge(userQuestion);
        if (!ragContext.isEmpty()) {
            systemPrompt += "\n\n【参考知识（请优先基于以下内容回答用户的常识类问题）】：\n" + ragContext;
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 注入对话历史（含 AI 回复，让 LLM 能理解上下文指代）
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                AgentHistoryMessage h = history.get(i);
                messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
            }
        }

        messages.add(Map.of("role", "user", "content", userQuestion));

        // ========== Agentic Loop ==========
        final int MAX_LOOPS = 5;

        for (int loop = 0; loop < MAX_LOOPS; loop++) {
            JSONObject llmResponse = callLlmWithTools(messages);
            if (llmResponse == null) {
                AgentChatResponse degradedResponse = buildReservationFollowUpDegradedResponse(
                        userQuestion, history, "llm_unavailable");
                if (degradedResponse != null) {
                    return degradedResponse;
                }
                SimpleStationQuery fallbackRoute = resolveSimpleStationQuery(userQuestion);
                if (fallbackRoute.matched()) {
                    log.info("simple_route_fallback | requestId={} | keyword={} | chargingType={} | reason=llm_unavailable",
                            currentRequestId.get(), fallbackRoute.keyword(), fallbackRoute.chargingType());
                    return stationDiscoveryService.buildStationCardResponse(currentLat.get(), currentLng.get(),
                            fallbackRoute.keyword(), fallbackRoute.chargingType(), 3);
                }
                return AgentChatResponse.text(buildModelUnavailableMessage(currentLlmFailureType.get()));
            }

            JSONObject choice = llmResponse.getJSONArray("choices").getJSONObject(0);
            JSONObject assistantMessage = choice.getJSONObject("message");
            String finishReason = choice.getString("finish_reason");
            log.info("llm_response_received | requestId={} | loop={} | finishReason={} | hasToolCalls={}",
                    currentRequestId.get(), loop, finishReason,
                    assistantMessage.containsKey("tool_calls"));

            if ("tool_calls".equals(finishReason) && assistantMessage.containsKey("tool_calls")) {
                // LLM 要调工具 → 执行工具，把结果加入 messages，继续循环
                JSONArray toolCalls = assistantMessage.getJSONArray("tool_calls");

                @SuppressWarnings("unchecked")
                Map<String, Object> assistantMap = assistantMessage.toJavaObject(Map.class);
                messages.add(assistantMap);

                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject toolCall = toolCalls.getJSONObject(i);
                    String toolCallId = toolCall.getString("id");
                    String functionName = toolCall.getJSONObject("function").getString("name");
                    String arguments = toolCall.getJSONObject("function").getString("arguments");

                    log.info("Agent 调用工具 | requestId={} | loop={} | function={} | arguments={}",
                            currentRequestId.get(), loop, functionName, arguments);

                    String functionResult = executeFunction(functionName, arguments);
                    toolRecords.add(new ToolRecord(functionName, arguments, functionResult));

                    Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                    toolResultMsg.put("role", "tool");
                    toolResultMsg.put("tool_call_id", toolCallId);
                    toolResultMsg.put("content", functionResult);
                    messages.add(toolResultMsg);
                }
                // 继续循环，让 LLM 看到工具结果后决定下一步
            } else {
                // finishReason == "stop" or "length" (truncated)
                log.info("agent_final_response | requestId={} | tools={} | finishReason={}",
                        currentRequestId.get(), toolRecords.stream().map(r -> r.name).toList(), finishReason);
                String content = assistantMessage.getString("content");
                if (content != null && !content.isBlank()) {
                    String text = content.trim();
                    if ("length".equals(finishReason)) {
                        log.warn("llm_response_truncated | requestId={} | contentLength={}",
                                currentRequestId.get(), text.length());
                    }
                    if (!toolRecords.isEmpty()) {
                        return buildCardResponse(text, toolRecords, history);
                    } else {
                        return AgentChatResponse.text(text);
                    }
                }

                // content is empty
                if ("length".equals(finishReason)) {
                    log.warn("llm_response_empty_truncated | requestId={}", currentRequestId.get());
                    return AgentChatResponse.text(
                            "回复内容过长被截断了，请尝试简化您的问题，例如直接说\"预约第一个站点，现在开始两小时\"。");
                }

                String fallbackAnswer = buildFallbackAnswer(toolRecords);
                if (!fallbackAnswer.isBlank()) {
                    return AgentChatResponse.text(fallbackAnswer);
                }

                AgentChatResponse degradedResponse = buildReservationFollowUpDegradedResponse(
                        userQuestion, history, "empty_llm_content");
                if (degradedResponse != null) {
                    return degradedResponse;
                }
                return AgentChatResponse.text("抱歉，我暂时无法整理查询结果。");
            }
        }

        // 达到最大循环次数，用最后一轮工具结果构建响应
        log.warn("agent_max_loops_reached | requestId={} | maxLoops={}", currentRequestId.get(), MAX_LOOPS);
        String fallbackAnswer = buildFallbackAnswer(toolRecords);
        if (!fallbackAnswer.isBlank()) {
            return AgentChatResponse.text(fallbackAnswer);
        }

        AgentChatResponse degradedResponse = buildReservationFollowUpDegradedResponse(
                userQuestion, history, "agent_max_loops");
        if (degradedResponse != null) {
            return degradedResponse;
        }
        return AgentChatResponse.text("抱歉，处理过程过于复杂，请简化您的问题后重试。");
    }

    // ==================== 卡片类型判断 ====================

    /**
     * 根据 tool 调用记录，智能判断应该附加什么类型的卡片
     */
    private AgentChatResponse buildCardResponse(String textContent, List<ToolRecord> toolRecords,
                                                List<AgentHistoryMessage> history) {
        String lastToolName = toolRecords.get(toolRecords.size() - 1).name;
        String result = toolRecords.get(toolRecords.size() - 1).result;

        try {
            return switch (lastToolName) {
                case "query_all_stations", "query_station_by_keyword" -> {
                    yield AgentChatResponse.of("stations", textContent, extractStationCardData(result));
                }
                case "query_station_detail" -> {
                    yield AgentChatResponse.of("station_detail", textContent, JSON.parseObject(result));
                }
                case "query_available_spots", "query_available_spots_across_stations" -> {
                    yield AgentChatResponse.of("available_spots", textContent, extractSpotCardData(result));
                }
                case "recommend_booking" -> {
                    yield buildRecommendBookingResponse(toolRecords, history, textContent);
                }
                case "query_user_reservations" -> {
                    yield AgentChatResponse.of("reservations", textContent, JSON.parseArray(result));
                }
                default -> AgentChatResponse.text(textContent);
            };
        } catch (Exception e) {
            log.warn("card_response_parse_failed | requestId={} | message={}", currentRequestId.get(), e.getMessage());
            return AgentChatResponse.text(textContent);
        }
    }

    /**
     * 从 query_all_stations/query_station_by_keyword 的 JSON 结果中提取卡片数据
     */
    private List<Object> extractStationCardData(String json) {
        try {
            if (json != null && json.startsWith("[")) {
                return JSON.parseArray(json).toJavaList(Object.class);
            }
        } catch (Exception e) {
            log.warn("提取站点卡片数据失败", e);
        }
        return List.of();
    }

    private AgentChatResponse buildRecommendBookingResponse(List<ToolRecord> toolRecords,
                                                            List<AgentHistoryMessage> history,
                                                            String textContent) {
        ToolRecord recommendRecord = toolRecords.get(toolRecords.size() - 1);
        RecommendBookingCardData cardData = parseRecommendBookingCardData(recommendRecord.result);
        if (cardData == null) {
            return AgentChatResponse.text(textContent);
        }
        if (currentUserId.get() == null) {
            return AgentChatResponse.text("请先登录后再进行预约操作。");
        }

        CrossStationBookingContext crossStationContext = resolveCrossStationBookingContext(toolRecords, cardData);
        String requestedStationName = crossStationContext != null
                ? crossStationContext.requestedStationName()
                : cardData.getStationName();
        RecommendBookingCardData normalizedCardData = normalizeRecommendBookingCardData(cardData, requestedStationName);

        if (crossStationContext != null && crossStationContext.switchedStation()) {
            PendingReservationContext pendingContext = findLatestPendingReservationContext(history);
            invalidatePendingReservation(pendingContext != null ? pendingContext.confirmToken() : null);
            return AgentChatResponse.of(
                    "recommend_booking",
                    buildCrossStationRecommendationMessage(requestedStationName, normalizedCardData),
                    toRecommendBookingMap(normalizedCardData));
        }

        PendingReservationContext pendingContext = findLatestPendingReservationContext(history);
        return createPendingReservationResponse(
                normalizedCardData,
                pendingContext != null ? pendingContext.confirmToken() : null);
    }

    private RecommendBookingCardData parseRecommendBookingCardData(String json) {
        try {
            if (json == null || !json.startsWith("{")) {
                return null;
            }
            JSONObject data = JSON.parseObject(json);
            if (data == null || data.getLong("spot_id") == null) {
                return null;
            }
            return RecommendBookingCardData.builder()
                    .stationId(data.getLong("station_id"))
                    .stationName(data.getString("station_name"))
                    .spotId(data.getLong("spot_id"))
                    .spotCode(data.getString("spot_code"))
                    .chargingType(data.getString("charging_type"))
                    .startTime(data.getString("start_time"))
                    .endTime(data.getString("end_time"))
                    .pricePerHour(data.getBigDecimal("price_per_hour"))
                    .reason(data.getString("reason"))
                    .build();
        } catch (Exception e) {
            log.warn("recommend_booking_parse_failed | requestId={} | message={}", currentRequestId.get(), e.getMessage());
            return null;
        }
    }

    private CrossStationBookingContext resolveCrossStationBookingContext(List<ToolRecord> toolRecords,
                                                                         RecommendBookingCardData cardData) {
        ToolRecord acrossRecord = findLatestToolRecord(toolRecords, "query_available_spots_across_stations");
        if (acrossRecord == null || acrossRecord.result == null || !acrossRecord.result.startsWith("{")) {
            return null;
        }

        try {
            JSONObject arguments = acrossRecord.arguments != null && !acrossRecord.arguments.isBlank()
                    ? JSON.parseObject(acrossRecord.arguments)
                    : new JSONObject();
            JSONObject result = JSON.parseObject(acrossRecord.result);

            Long preferredStationId = arguments.getLong("station_id");
            Long resolvedStationId = result.getLong("station_id");
            Integer stationOrder = result.getInteger("station_order");

            String requestedStationName = null;
            JSONArray checkedStations = result.getJSONArray("checked_stations");
            if (checkedStations != null && !checkedStations.isEmpty()) {
                requestedStationName = checkedStations.getJSONObject(0).getString("station_name");
            }
            if ((requestedStationName == null || requestedStationName.isBlank()) && preferredStationId != null) {
                requestedStationName = stationService.findById(preferredStationId)
                        .map(ChargingStationDTO::getName)
                        .orElse(null);
            }

            boolean switchedStation = stationOrder != null && stationOrder > 1;
            if (!switchedStation && preferredStationId != null && resolvedStationId != null) {
                switchedStation = !preferredStationId.equals(resolvedStationId);
            }
            if (!switchedStation
                    && requestedStationName != null
                    && cardData.getStationName() != null
                    && !requestedStationName.equals(cardData.getStationName())
                    && checkedStations != null
                    && checkedStations.size() > 1) {
                switchedStation = true;
            }
            return new CrossStationBookingContext(switchedStation, requestedStationName);
        } catch (Exception e) {
            log.warn("cross_station_context_parse_failed | requestId={} | message={}",
                    currentRequestId.get(), e.getMessage());
            return null;
        }
    }

    private ToolRecord findLatestToolRecord(List<ToolRecord> toolRecords, String name) {
        if (toolRecords == null || toolRecords.isEmpty()) {
            return null;
        }
        for (int i = toolRecords.size() - 1; i >= 0; i--) {
            ToolRecord record = toolRecords.get(i);
            if (record != null && Objects.equals(record.name, name)) {
                return record;
            }
        }
        return null;
    }

    /**
     * 从 query_available_spots 的 JSON 结果中提取卡片数据
     */
    private List<Object> extractSpotCardData(String json) {
        try {
            if (json != null && json.startsWith("[")) {
                return JSON.parseArray(json).toJavaList(Object.class);
            }
            if (json != null && json.startsWith("{")) {
                JSONObject obj = JSON.parseObject(json);
                JSONArray spots = obj.getJSONArray("spots");
                if (spots != null) {
                    return spots.toJavaList(Object.class);
                }
            }
        } catch (Exception e) {
            log.warn("提取车位卡片数据失败", e);
        }
        return List.of();
    }

    // ==================== 工具执行引擎 ====================

    private String executeFunction(String functionName, String arguments) {
        try {
            return switch (functionName) {
                case "query_all_stations" -> queryAllStations();
                case "query_station_by_keyword" -> {
                    QueryStationByKeywordArgs args = parseArgs(arguments, QueryStationByKeywordArgs.class);
                    yield queryStationByKeyword(args.getKeyword());
                }
                case "query_station_detail" -> {
                    JSONObject args = JSON.parseObject(arguments);
                    yield queryStationDetail(args.getLong("station_id"));
                }
                case "query_available_spots" -> {
                    QueryAvailableSpotsArgs args = parseArgs(arguments, QueryAvailableSpotsArgs.class);
                    yield queryAvailableSpots(
                            args.getStationId(),
                            args.getStartTime(),
                            args.getEndTime(),
                            args.getChargingType());
                }
                case "query_available_spots_across_stations" -> {
                    QueryAvailableSpotsArgs args = parseArgs(arguments, QueryAvailableSpotsArgs.class);
                    yield queryAvailableSpotsAcrossStations(
                            args.getStationId(),
                            args.getStartTime(),
                            args.getEndTime(),
                            args.getChargingType(),
                            args.getKeyword(),
                            args.getMaxStations());
                }
                case "recommend_booking" -> {
                    JSONObject args = JSON.parseObject(arguments);
                    yield recommendBooking(
                            args.getLong("spot_id"),
                            args.getString("start_time"),
                            args.getString("end_time"));
                }
                case "query_user_reservations" -> queryUserReservations();
                default -> "未知工具: " + functionName;
            };
        } catch (Exception e) {
            log.error("tool_execution_failed | requestId={} | function={}", currentRequestId.get(), functionName, e);
            return "工具执行失败，请稍后再试。";
        }
    }

    // ==================== 新增工具函数 ====================

    /**
     * 工具4：查询指定站点在指定时间段内的可用车位
     */
    private String queryAvailableSpots(Long stationId, String startTimeStr, String endTimeStr, String chargingType) {
        if (stationId == null) return "请提供充电站ID";
        if (startTimeStr == null || endTimeStr == null) return "请提供开始和结束时间";

        try {
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            String normalizedChargingType = normalizeReservationChargingType(chargingType);
            if (!endTime.isAfter(startTime)) {
                return "结束时间必须晚于开始时间";
            }
            if (Duration.between(startTime, endTime).toMinutes() > 12 * 60) {
                return "单次预约时长不能超过12小时";
            }

            List<ParkingSpot> allSpots = parkingSpotService.findAvailableSpotsForTime(startTime, endTime);

            // 过滤出属于目标站点的车位
            List<AvailableSpotCardData> result = new ArrayList<>();
            for (ParkingSpot spot : allSpots) {
                if (spot.getPile() != null && spot.getPile().getStation() != null
                        && spot.getPile().getStation().getId().equals(stationId)) {
                    String pileType = spot.getPile() != null ? spot.getPile().getPileType() : null;
                    if (normalizedChargingType != null && !normalizedChargingType.equalsIgnoreCase(pileType)) {
                        continue;
                    }

                    result.add(AvailableSpotCardData.builder()
                            .spotId(spot.getId())
                            .spotCode(spot.getSpotCode())
                            .spotType(spot.getSpotType())
                            .chargingType(pileType != null ? displayChargingType(pileType) : null)
                            .pricePerHour(spot.getPricePerHour())
                            .serviceFee(spot.getServiceFee())
                            .build());
                }
            }

            if (result.isEmpty()) {
                if (normalizedChargingType != null) {
                    return "该充电站在指定时间段内没有可用的" + displayChargingType(normalizedChargingType) + "车位";
                }
                return "该充电站在指定时间段内没有可用车位";
            }
            return JSON.toJSONString(toAvailableSpotMaps(result));
        } catch (Exception e) {
            log.error("query_available_spots_failed | requestId={} | stationId={} | startTime={} | endTime={} | chargingType={}",
                    currentRequestId.get(), stationId, startTimeStr, endTimeStr, chargingType, e);
            return "查询可用车位失败，请稍后再试。";
        }
    }

    /**
     * 工具5：按推荐顺序跨站点查找可用车位
     * 当前站点不满足快充/慢充条件时，继续检查后续推荐站点。
     */
    private String queryAvailableSpotsAcrossStations(Long preferredStationId, String startTimeStr, String endTimeStr,
                                                     String chargingType, String keyword, Integer maxStations) {
        if (startTimeStr == null || endTimeStr == null) return "请提供开始和结束时间";

        try {
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            String normalizedChargingType = normalizeReservationChargingType(chargingType);
            if (!endTime.isAfter(startTime)) {
                return "结束时间必须晚于开始时间";
            }
            if (Duration.between(startTime, endTime).toMinutes() > 12 * 60) {
                return "单次预约时长不能超过12小时";
            }

            List<StationCardData> candidates = buildStationCandidates(preferredStationId, keyword,
                    normalizedChargingType, maxStations);
            if (candidates.isEmpty()) {
                if (normalizedChargingType != null) {
                    return "当前没有找到支持" + displayChargingType(normalizedChargingType) + "的候选充电站。";
                }
                return "当前没有找到可用于预约的候选充电站。";
            }

            List<ParkingSpot> allSpots = parkingSpotService.findAvailableSpotsForTime(startTime, endTime);
            List<Map<String, Object>> checkedStations = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                StationCardData station = candidates.get(i);
                List<AvailableSpotCardData> spots = collectAvailableSpotsForStation(
                        allSpots, station.getId(), normalizedChargingType);
                checkedStations.add(toCheckedStationMap(station, i + 1, spots.size()));

                if (!spots.isEmpty()) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("station_id", station.getId());
                    result.put("station_name", station.getName());
                    result.put("station_order", i + 1);
                    if (station.getDistanceKm() != null) {
                        result.put("distance_km", station.getDistanceKm());
                    }
                    result.put("charging_type", normalizedChargingType != null ? displayChargingType(normalizedChargingType) : null);
                    result.put("start_time", startTime.toString());
                    result.put("end_time", endTime.toString());
                    result.put("spots", toAvailableSpotMaps(spots));
                    result.put("checked_stations", checkedStations);
                    result.put("message", buildAcrossStationSuccessMessage(station, i + 1, normalizedChargingType));
                    return JSON.toJSONString(result);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("charging_type", normalizedChargingType != null ? displayChargingType(normalizedChargingType) : null);
            result.put("start_time", startTime.toString());
            result.put("end_time", endTime.toString());
            result.put("checked_stations", checkedStations);
            result.put("message", buildAcrossStationEmptyMessage(candidates.size(), normalizedChargingType));
            return JSON.toJSONString(result);
        } catch (Exception e) {
            log.error("query_available_spots_across_stations_failed | requestId={} | preferredStationId={} | startTime={} | endTime={} | chargingType={}",
                    currentRequestId.get(), preferredStationId, startTimeStr, endTimeStr, chargingType, e);
            return "跨站点查询可用车位失败，请稍后再试。";
        }
    }

    private List<AvailableSpotCardData> collectAvailableSpotsForStation(List<ParkingSpot> allSpots, Long stationId,
                                                                        String normalizedChargingType) {
        List<AvailableSpotCardData> result = new ArrayList<>();
        if (allSpots == null || stationId == null) {
            return result;
        }

        for (ParkingSpot spot : allSpots) {
            if (spot.getPile() == null || spot.getPile().getStation() == null
                    || !stationId.equals(spot.getPile().getStation().getId())) {
                continue;
            }
            String pileType = spot.getPile().getPileType();
            if (normalizedChargingType != null && !normalizedChargingType.equalsIgnoreCase(normalizeChargingType(pileType))) {
                continue;
            }
            result.add(AvailableSpotCardData.builder()
                    .spotId(spot.getId())
                    .spotCode(spot.getSpotCode())
                    .spotType(spot.getSpotType())
                    .chargingType(pileType != null ? displayChargingType(pileType) : null)
                    .pricePerHour(spot.getPricePerHour())
                    .serviceFee(spot.getServiceFee())
                    .build());
        }
        return result;
    }

    private List<StationCardData> buildStationCandidates(Long preferredStationId, String keyword,
                                                         String normalizedChargingType, Integer maxStations) {
        int limit = maxStations != null && maxStations > 0 ? Math.min(maxStations, 20) : 10;
        StationDiscoveryService.StationDiscoveryResult discoveryResult = stationDiscoveryService.queryNearbyStations(
                currentLat.get(), currentLng.get(), keyword, normalizedChargingType, limit);
        List<StationCardData> candidates = new ArrayList<>(discoveryResult.stations());

        if (preferredStationId == null) {
            return candidates;
        }

        Optional<StationCardData> selected = candidates.stream()
                .filter(station -> preferredStationId.equals(station.getId()))
                .findFirst();
        if (selected.isPresent()) {
            candidates.removeIf(station -> preferredStationId.equals(station.getId()));
            candidates.add(0, selected.get());
            return candidates;
        }

        stationService.findById(preferredStationId).ifPresent(station -> candidates.add(0,
                StationCardData.builder()
                        .id(station.getId())
                        .name(station.getName())
                        .address(station.getAddress())
                        .city(station.getCity())
                        .pileCount(station.getPileCount())
                        .availablePileCount(station.getAvailablePileCount())
                        .businessHours(station.getBusinessHours())
                        .build()));
        return candidates;
    }

    private Map<String, Object> toCheckedStationMap(StationCardData station, int order, int availableSpotCount) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("order", order);
        info.put("station_id", station.getId());
        info.put("station_name", station.getName());
        if (station.getDistanceKm() != null) {
            info.put("distance_km", station.getDistanceKm());
        }
        info.put("available_spot_count", availableSpotCount);
        return info;
    }

    private String buildAcrossStationSuccessMessage(StationCardData station, int order, String chargingType) {
        StringBuilder message = new StringBuilder();
        if (order == 1) {
            message.append("已在你指定的站点找到");
        } else {
            message.append("你指定的站点没有符合条件的车位，已继续检查第 ").append(order).append(" 个推荐站点，并找到");
        }
        message.append(chargingType != null ? displayChargingType(chargingType) : "可用").append("车位");
        if (station.getName() != null && !station.getName().isBlank()) {
            message.append("，站点是 ").append(station.getName());
        }
        message.append("。");
        return message.toString();
    }

    private String buildAcrossStationEmptyMessage(int checkedCount, String chargingType) {
        return "已按推荐顺序检查 " + checkedCount + " 个站点，但指定时间段内暂未找到可用的"
                + (chargingType != null ? displayChargingType(chargingType) : "")
                + "车位，建议更换预约时间或充电类型。";
    }

    /**
     * 工具5：推荐预约方案 — 查询车位信息并返回推荐参数，不创建预约
     * 前端收到后引导用户跳转 StationDetail 页面确认
     */
    private String recommendBooking(Long spotId, String startTimeStr, String endTimeStr) {
        if (spotId == null) return "请提供车位ID";
        if (startTimeStr == null || endTimeStr == null) return "请提供开始和结束时间";

        try {
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME);

            Optional<String> validationError = reservationService.validateReservationRequest(spotId, startTime, endTime);
            if (validationError.isPresent()) {
                return validationError.get();
            }

            Optional<ParkingSpot> spotOpt = parkingSpotService.findById(spotId);
            if (spotOpt.isEmpty()) {
                return "车位ID " + spotId + " 不存在";
            }

            ParkingSpot spot = spotOpt.get();
            String spotCode = spot.getSpotCode();
            String stationName = "";
            Long stationId = null;
            String pileType = null;
            if (spot.getPile() != null) {
                pileType = spot.getPile().getPileType();
                if (spot.getPile().getStation() != null) {
                    stationName = spot.getPile().getStation().getName();
                    stationId = spot.getPile().getStation().getId();
                }
            }

            RecommendBookingCardData result = RecommendBookingCardData.builder()
                    .stationId(stationId)
                    .stationName(stationName)
                    .spotId(spotId)
                    .spotCode(spotCode)
                    .chargingType(pileType != null ? displayChargingType(pileType) : null)
                    .startTime(startTime.toString())
                    .endTime(endTime.toString())
                    .pricePerHour(spot.getPricePerHour())
                    .reason("基于您的需求推荐" + (pileType != null ? displayChargingType(pileType) : "") + "车位")
                    .build();

            return JSON.toJSONString(toRecommendBookingMap(result));
        } catch (Exception e) {
            log.error("recommend_booking_failed | requestId={} | spotId={} | startTime={} | endTime={}",
                    currentRequestId.get(), spotId, startTimeStr, endTimeStr, e);
            return "推荐预约方案失败，请稍后再试。";
        }
    }

    /**
     * 工具6：查询当前用户的预约列表
     */
    private String queryUserReservations() {
        Long userId = currentUserId.get();
        if (userId == null) {
            return "用户未登录，无法查询预约。请提示用户先登录。";
        }

        try {
            List<ReservationDTO> reservations = reservationService.findByUserId(userId);
            if (reservations.isEmpty()) {
                return "您当前没有预约记录";
            }

            List<UserReservationCardData> result = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (ReservationDTO r : reservations) {
                result.add(UserReservationCardData.builder()
                        .reservationId(r.getId())
                        .stationName(r.getStationName() != null ? r.getStationName() : "未知站点")
                        .spotCode(r.getSpotCode() != null ? r.getSpotCode() : "未知车位")
                        .startTime(r.getStartTime() != null ? r.getStartTime().format(fmt) : "")
                        .endTime(r.getEndTime() != null ? r.getEndTime().format(fmt) : "")
                        .status(reservationStatusText(r.getStatus()))
                        .build());
            }
            return JSON.toJSONString(toUserReservationMaps(result));
        } catch (Exception e) {
            log.error("query_user_reservations_failed | requestId={} | userId={}", currentRequestId.get(), userId, e);
            return "查询预约失败，请稍后再试。";
        }
    }

    private String reservationStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "已取消";
            case 1 -> "待使用";
            case 2 -> "使用中";
            case 3 -> "已完成";
            default -> "未知";
        };
    }

    // ==================== 原有工具函数 ====================

    private String queryAllStations() {
        return stationDiscoveryService.buildStationJson(currentLat.get(), currentLng.get(), null, null, 3);
    }

    private String queryStationByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return "请提供搜索关键词";
        return stationDiscoveryService.buildStationJson(currentLat.get(), currentLng.get(), keyword, null, 3);
    }

    private String queryStationDetail(Long stationId) {
        if (stationId == null) return "请提供充电站ID";

        Optional<ChargingStationDTO> stationOpt = stationService.findById(stationId);
        if (stationOpt.isEmpty()) return "充电站ID " + stationId + " 不存在";

        ChargingStationDTO station = stationOpt.get();

        StationDetailCardData result = StationDetailCardData.builder()
                .id(station.getId())
                .name(station.getName())
                .address(station.getAddress())
                .businessHours(station.getBusinessHours())
                .contact(station.getContact())
                .pileCount(station.getPileCount())
                .availablePileCount(station.getAvailablePileCount())
                .build();

        if (station.getPiles() != null) {
            List<Map<String, Object>> pileList = new ArrayList<>();
            for (var pile : station.getPiles()) {
                Map<String, Object> pileInfo = new LinkedHashMap<>();
                pileInfo.put("桩编号", pile.getPileCode());
                pileInfo.put("类型", pile.getPileType());
                pileInfo.put("功率", pile.getPower() + "kW");
                pileInfo.put("状态", pile.getStatus() == 1 ? "空闲可用" : pile.getStatus() == 2 ? "充电中" : "离线");
                // 添加车位信息，方便前端展示和预约
                if (pile.getParkingSpots() != null) {
                    List<Map<String, Object>> spots = new ArrayList<>();
                    for (var spot : pile.getParkingSpots()) {
                        Map<String, Object> spotInfo = new LinkedHashMap<>();
                        spotInfo.put("spot_id", spot.getId());
                        spotInfo.put("车位编号", spot.getSpotCode());
                        spotInfo.put("状态", spot.getStatus() == 1 ? "空闲" : spot.getStatus() == 2 ? "已预约" : "使用中");
                        spotInfo.put("每小时价格", spot.getPricePerHour());
                        spots.add(spotInfo);
                    }
                    pileInfo.put("车位列表", spots);
                }
                pileList.add(pileInfo);
            }
            result.setPiles(pileList);
        }

        return JSON.toJSONString(toStationDetailMap(result));
    }

    // ==================== 辅助方法 ====================

    private String extractAssistantContent(JSONObject response) {
        if (response == null) return null;
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null) return null;
        String content = message.getString("content");
        return (content != null && !content.isBlank()) ? content.trim() : null;
    }

    private String buildFallbackAnswer(List<ToolRecord> toolRecords) {
        if (toolRecords == null || toolRecords.isEmpty()) return "";
        // 优先用最后一个工具的结果生成 fallback
        for (int i = toolRecords.size() - 1; i >= 0; i--) {
            ToolRecord record = toolRecords.get(i);
            String fallback = switch (record.name) {
                case "query_all_stations", "query_station_by_keyword" -> summarizeStationList(record.result);
                case "query_station_detail" -> summarizeStationDetail(record.result);
                case "query_available_spots", "query_available_spots_across_stations" -> summarizeAvailableSpots(record.result);
                case "recommend_booking" -> summarizeRecommendation(record.result);
                case "query_user_reservations" -> summarizeUserReservations(record.result);
                default -> "";
            };
            if (!fallback.isBlank()) return fallback;
        }
        return "";
    }

    private String summarizeStationList(String json) {
        if (json == null || json.isBlank() || !json.startsWith("[")) return json != null ? json : "";
        try {
            JSONArray stations = JSON.parseArray(json);
            if (stations == null || stations.isEmpty()) return "当前没有查到可用的充电站。";
            int displayCount = Math.min(stations.size(), 3);
            StringBuilder sb = new StringBuilder();
            sb.append("已为你找到 ").append(displayCount).append(" 个较合适的充电站，按距离由近到远排列：");
            for (int i = 0; i < displayCount; i++) {
                JSONObject s = stations.getJSONObject(i);
                sb.append("\n").append(i + 1).append(". ");
                if (s.containsKey("距离(km)")) sb.append("约 ").append(s.get("距离(km)")).append(" km");
                else sb.append(defaultText(s.getString("名称"), "未命名"));
                int available = s.getIntValue("当前可用桩数", -1);
                int total = s.getIntValue("总桩数", -1);
                if (available > 0) {
                    sb.append("，有空闲桩（").append(available).append("/").append(total).append("）");
                    if (i == 0) sb.append("，建议优先查看");
                    else if (i == displayCount - 1 && displayCount > 1) sb.append("，可作为备选");
                } else if (available == 0) {
                    sb.append("，当前无空闲桩");
                }
            }
            if (stations.size() > displayCount)
                sb.append("\n\n还有 ").append(stations.size() - displayCount).append(" 个站点可选。点击下方卡片可查看详情与预约信息。");
            else
                sb.append("\n\n点击下方卡片可查看详情与预约信息。");
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String summarizeStationDetail(String json) {
        if (json == null || json.isBlank() || !json.startsWith("{")) return json != null ? json : "";
        try {
            JSONObject s = JSON.parseObject(json);
            StringBuilder sb = new StringBuilder();
            sb.append(defaultText(s.getString("名称"), "目标充电站")).append(" 详情：");
            sb.append("\n地址：").append(defaultText(s.getString("地址"), "暂无"));
            if (s.containsKey("当前可用桩数")) sb.append("\n可用桩：").append(s.get("当前可用桩数")).append("/").append(s.get("总桩数"));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String summarizeAvailableSpots(String json) {
        if (json == null || json.isBlank()) return "查询可用车位失败。";
        if (json.startsWith("{")) {
            try {
                JSONObject result = JSON.parseObject(json);
                String message = result.getString("message");
                JSONArray spots = result.getJSONArray("spots");
                if (spots == null || spots.isEmpty()) {
                    return message != null && !message.isBlank() ? message : "该时间段内没有可用车位。";
                }

                StringBuilder sb = new StringBuilder(message != null && !message.isBlank() ? message : "已找到可用车位。");
                sb.append("\n站点：").append(defaultText(result.getString("station_name"), "未知站点"));
                if (result.containsKey("distance_km")) {
                    sb.append("（约 ").append(result.get("distance_km")).append(" km）");
                }
                sb.append("\n可用车位：");
                for (int i = 0; i < spots.size(); i++) {
                    JSONObject s = spots.getJSONObject(i);
                    sb.append("\n").append(i + 1).append(". ").append(defaultText(s.getString("车位编号"), "未知"));
                    if (s.getString("充电类型") != null) {
                        sb.append("（").append(s.getString("充电类型")).append("）");
                    }
                    sb.append("，").append(s.get("每小时价格")).append("元/小时");
                }
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }
        if (!json.startsWith("[")) return json; // 已是错误文本
        try {
            JSONArray spots = JSON.parseArray(json);
            if (spots == null || spots.isEmpty()) return "该时间段内没有可用车位。";
            StringBuilder sb = new StringBuilder("可用车位：\n");
            for (int i = 0; i < spots.size(); i++) {
                JSONObject s = spots.getJSONObject(i);
                sb.append(i + 1).append(". ").append(defaultText(s.getString("车位编号"), "未知"));
                sb.append("（").append(defaultText(s.getString("类型"), "标准")).append("）");
                sb.append("，").append(s.get("每小时价格")).append("元/小时");
                if (i < spots.size() - 1) sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String summarizeRecommendation(String json) {
        if (json == null || json.isBlank()) return "推荐预约方案失败。";
        try {
            JSONObject r = JSON.parseObject(json);
            if (r.containsKey("spot_id")) {
                StringBuilder sb = new StringBuilder("已为您推荐预约方案：");
                sb.append("\n站点：").append(defaultText(r.getString("station_name"), "未知"));
                sb.append("\n车位：").append(defaultText(r.getString("spot_code"), "未知"));
                sb.append("\n时间：").append(defaultText(r.getString("start_time"), "")).append(" 至 ").append(defaultText(r.getString("end_time"), ""));
                sb.append("\n请点击确认预约。");
                return sb.toString();
            }
            return json;
        } catch (Exception e) { return ""; }
    }

    private String summarizeUserReservations(String json) {
        if (json == null || json.isBlank()) return "查询预约记录失败。";
        if (!json.startsWith("[")) return json;
        try {
            JSONArray reservations = JSON.parseArray(json);
            if (reservations == null || reservations.isEmpty()) return "您当前没有预约记录。";
            StringBuilder sb = new StringBuilder("您的预约记录：\n");
            for (int i = 0; i < reservations.size(); i++) {
                JSONObject r = reservations.getJSONObject(i);
                sb.append(i + 1).append(". ").append(defaultText(r.getString("station_name"), "未知站点"));
                sb.append(" - ").append(defaultText(r.getString("spot_code"), "未知车位"));
                sb.append("（").append(defaultText(r.getString("status"), "未知")).append("）");
                if (i < reservations.size() - 1) sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String defaultText(String text, String fallback) {
        return text != null && !text.isBlank() ? text : fallback;
    }

    private <T> T parseArgs(String arguments, Class<T> clazz) {
        return JSON.parseObject(arguments, clazz, JSONReader.Feature.SupportSmartMatch);
    }

    private List<Map<String, Object>> toAvailableSpotMaps(List<AvailableSpotCardData> spots) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AvailableSpotCardData spot : spots) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("spot_id", spot.getSpotId());
            info.put("车位编号", spot.getSpotCode());
            info.put("类型", spot.getSpotType());
            if (spot.getChargingType() != null) {
                info.put("充电类型", spot.getChargingType());
            }
            info.put("每小时价格", spot.getPricePerHour());
            info.put("服务费(元/度)", spot.getServiceFee());
            result.add(info);
        }
        return result;
    }

    private Map<String, Object> toRecommendBookingMap(RecommendBookingCardData data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("station_id", data.getStationId());
        result.put("station_name", data.getStationName());
        result.put("spot_id", data.getSpotId());
        result.put("spot_code", data.getSpotCode());
        if (data.getChargingType() != null) {
            result.put("charging_type", data.getChargingType());
        }
        result.put("start_time", data.getStartTime());
        result.put("end_time", data.getEndTime());
        result.put("price_per_hour", data.getPricePerHour());
        result.put("reason", data.getReason());
        return result;
    }

    private List<Map<String, Object>> toUserReservationMaps(List<UserReservationCardData> reservations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserReservationCardData reservation : reservations) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("reservation_id", reservation.getReservationId());
            info.put("station_name", reservation.getStationName());
            info.put("spot_code", reservation.getSpotCode());
            info.put("start_time", reservation.getStartTime());
            info.put("end_time", reservation.getEndTime());
            info.put("status", reservation.getStatus());
            result.add(info);
        }
        return result;
    }

    private Map<String, Object> toStationDetailMap(StationDetailCardData data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", data.getId());
        result.put("名称", data.getName());
        result.put("地址", data.getAddress());
        result.put("营业时间", data.getBusinessHours());
        result.put("联系电话", data.getContact());
        result.put("总桩数", data.getPileCount());
        result.put("当前可用桩数", data.getAvailablePileCount());
        if (data.getPiles() != null) {
            result.put("充电桩详情", data.getPiles());
        }
        return result;
    }

    private SimpleStationQuery resolveSimpleStationQuery(String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return SimpleStationQuery.notMatched();
        }

        String normalized = normalizeQuestion(userQuestion);
        if (containsAny(normalized, List.of(
                "预约", "预定", "帮我约", "确认", "取消", "签到", "签退",
                "订单", "支付", "车位", "详情"))) {
            return SimpleStationQuery.notMatched();
        }

        boolean hasStationNoun = containsAny(normalized, List.of("充电站", "充电桩", "超充站", "快充站", "慢充站", "站点"));
        boolean hasDiscoveryVerb = containsAny(normalized, List.of(
                "附近", "周边", "查询", "查", "看看", "看下", "看一下", "搜索",
                "搜", "找", "推荐", "有什么", "有哪些", "有哪", "哪里有", "哪儿有", "有没有"));

        if (!hasStationNoun || !hasDiscoveryVerb) {
            return SimpleStationQuery.notMatched();
        }

        String chargingType = extractDiscoveryChargingType(userQuestion);
        String keyword = extractStationKeyword(userQuestion);
        if (chargingType != null && keyword != null
                && containsAny(normalizeQuestion(keyword), List.of("快充", "慢充", "直流", "交流", "dc", "ac", "可用"))) {
            keyword = null;
        }
        return new SimpleStationQuery(true, keyword, chargingType);
    }

    private String extractStationKeyword(String userQuestion) {
        String keyword = userQuestion;
        List<String> phrasesToRemove = List.of(
                "帮我查询一下", "帮我查一下", "帮我查查", "帮我看看", "帮我看下", "帮我找一下", "帮我找找",
                "帮我搜索一下", "帮我搜一下", "请帮我查询一下", "请帮我查一下", "请帮我看看",
                "帮我查询", "帮我查", "帮我找", "帮我搜索", "帮我搜", "请帮我", "麻烦帮我", "麻烦",
                "查询一下", "查一下", "搜索一下", "搜一下", "看一下", "看下", "看看", "找一下", "找找",
                "查询", "搜索", "附近的", "周边的", "有什么", "有哪些", "有哪", "哪里有", "哪儿有", "有没有",
                "推荐一下", "推荐", "附近", "周边", "充电站", "充电桩", "超充站", "快充站", "慢充站", "站点",
                "可用的", "可预约的", "可预约", "支持",
                "空闲的", "空闲", "有空闲的", "有空闲", "空位的", "空位", "有空位的", "有空位",
                "有位置的", "有位置", "有位的", "有位", "有桩的", "有桩", "有空闲桩", "空闲桩", "有可用桩",
                "查", "搜", "找", "一下");

        for (String phrase : phrasesToRemove) {
            keyword = keyword.replace(phrase, " ");
        }

        keyword = keyword.replaceAll("[，。！？,.!?、；;：()（）\\[\\]{}\"“”‘’]", " ");
        keyword = keyword.replaceAll("\\s+", " ").trim();
        keyword = keyword.replaceAll("^(的|请|帮我|我想)+", "").trim();
        keyword = keyword.replaceAll("(的|吧|呢|呀|吗|么|嘛|啊|哦|哈|嘿)$", "").trim();

        // 去掉残留的语气词和助词
        keyword = keyword.replaceAll("(^|\\s+)(有|是|在|去|到|能|会|要|想|可|的|了|过|着|不|没|很|也|还|都|就|才|已|又|与|及|和|或)(?=\\s+|$)", " ").trim();
        keyword = keyword.replaceAll("\\s+", " ").trim();

        if (keyword.isBlank() || containsAny(keyword, List.of(
                "附近", "周边", "查询", "看看", "搜索", "充电站",
                "空闲", "可用", "空位", "有位", "有桩", "可预约"))) {
            return null;
        }
        return keyword;
    }

    private String extractDiscoveryChargingType(String userQuestion) {
        String normalized = normalizeQuestion(userQuestion);
        if (containsAny(normalized, List.of(
                "可用的快充", "快充充电桩", "快充桩", "快充有哪些", "附近快充", "附近的快充", "直流"))) {
            return "DC";
        }
        if (containsAny(normalized, List.of(
                "可用的慢充", "慢充充电桩", "慢充桩", "慢充有哪些", "附近慢充", "附近的慢充", "交流"))) {
            return "AC";
        }
        return null;
    }

    private String extractReservationChargingType(String userQuestion) {
        String normalized = normalizeQuestion(userQuestion);
        if (containsAny(normalized, List.of("要快充", "类型要快充", "类型快充", "快充车位", "快充", "直流", "dc"))) {
            return "DC";
        }
        if (containsAny(normalized, List.of("要慢充", "类型要慢充", "类型慢充", "慢充车位", "慢充", "交流", "ac"))) {
            return "AC";
        }
        return null;
    }

    private PreparsedReservationIntent resolvePreparsedReservationIntent(String userQuestion,
                                                                        List<AgentHistoryMessage> history) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return PreparsedReservationIntent.notMatched();
        }

        String normalized = normalizeQuestion(userQuestion);
        boolean crossStationFallbackRequest = isCrossStationFallbackRequest(normalized);
        if (!crossStationFallbackRequest
                && containsAny(normalized, List.of("确认", "取消", "改约", "换一个", "如果", "要是", "否则", "或者", "优先"))) {
            return PreparsedReservationIntent.notMatched();
        }

        SpotSelection spotSelection = resolveSpotSelection(userQuestion, history);
        StationSelection selection = spotSelection != null
                ? new StationSelection(spotSelection.stationId(), spotSelection.stationName())
                : resolveStationSelection(userQuestion, history);
        if (selection == null) {
            return PreparsedReservationIntent.notMatched();
        }

        PendingReservationContext pendingContext = findLatestPendingReservationContext(history);
        boolean pendingModification = isPendingReservationModification(userQuestion, pendingContext);

        TimeRange timeRange = parseTimeRange(userQuestion);
        if (timeRange == null && pendingModification) {
            timeRange = new TimeRange(pendingContext.startTime(), pendingContext.endTime());
        }
        if (timeRange == null) {
            return PreparsedReservationIntent.notMatched();
        }

        String chargingType = extractReservationChargingType(userQuestion);
        if (chargingType == null && pendingModification) {
            chargingType = pendingContext.chargingType();
        }
        if (chargingType == null) {
            chargingType = DEFAULT_RESERVATION_CHARGING_TYPE;
        }

        boolean hasReservationVerb = containsAny(normalized, List.of("预约", "预定", "帮我约", "帮我预约"));
        if (!hasReservationVerb
                && !pendingModification
                && !isImplicitReservationFollowUp(userQuestion, history, selection, timeRange)) {
            return PreparsedReservationIntent.notMatched();
        }

        return new PreparsedReservationIntent(
                true,
                selection.stationId(),
                selection.stationName(),
                timeRange.startTime(),
                timeRange.endTime(),
                chargingType,
                spotSelection != null ? spotSelection.spotId() : null,
                pendingModification ? pendingContext.confirmToken() : null);
    }

    private boolean isCrossStationFallbackRequest(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        boolean mentionsChargingType = containsAny(normalizedQuestion, List.of("快充", "慢充", "dc", "ac"));
        boolean mentionsFallback = containsAny(normalizedQuestion, List.of(
                "没有", "没空", "无空", "顺延", "下一个", "别的站", "其他站", "换站", "继续找"));
        return mentionsChargingType && mentionsFallback;
    }


    private boolean isImplicitReservationFollowUp(String userQuestion, List<AgentHistoryMessage> history,
                                                  StationSelection selection, TimeRange timeRange) {
        if (selection == null || timeRange == null) {
            return false;
        }

        StationContext context = findLatestStationContext(history);
        if (context == null || context.stations().isEmpty()) {
            return false;
        }

        String normalized = normalizeQuestion(userQuestion);
        return extractOrdinal(userQuestion) != null
                || context.stations().size() == 1
                || containsAny(normalized, List.of(
                        "这个", "那个", "它", "该站", "该充电站",
                        "快充", "慢充", "充电类型", "类型",
                        "现在", "今天", "明天"));
    }

    private PreparsedStationDetailIntent resolvePreparsedStationDetailIntent(String userQuestion,
                                                                             List<AgentHistoryMessage> history) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return PreparsedStationDetailIntent.notMatched();
        }

        String normalized = normalizeQuestion(userQuestion);
        if (containsAny(normalized, List.of("预约", "预定", "帮我约", "帮我预约", "支付", "订单", "签到", "签退"))) {
            return PreparsedStationDetailIntent.notMatched();
        }
        if (parseTimeRange(userQuestion) != null) {
            return PreparsedStationDetailIntent.notMatched();
        }

        StationSelection selection = resolveStationSelection(userQuestion, history);
        if (selection == null) {
            return PreparsedStationDetailIntent.notMatched();
        }

        if (!containsAny(normalized, List.of(
                "第一个", "第1个", "第二个", "第2个", "第三个", "第3个",
                "这个站", "那个站", "该站", "这个充电站", "那个充电站", "充电站",
                "详情", "详细", "信息", "看看", "看下", "查看"))) {
            return PreparsedStationDetailIntent.notMatched();
        }

        return new PreparsedStationDetailIntent(true, selection.stationId(), selection.stationName());
    }

    private AgentChatResponse executeDeterministicReservation(PreparsedReservationIntent intent) {
        if (currentUserId.get() == null) {
            return AgentChatResponse.text("请先登录后再进行预约操作。");
        }

        if (intent.spotId() != null) {
            String recommendationResult = recommendBooking(
                    intent.spotId(),
                    intent.startTime().toString(),
                    intent.endTime().toString());
            RecommendBookingCardData cardData = parseRecommendBookingCardData(recommendationResult);
            if (cardData == null) {
                return AgentChatResponse.text(recommendationResult != null ? recommendationResult : "暂时无法生成预约方案。");
            }
            return createPendingReservationResponse(cardData, intent.previousConfirmToken());
        }

        String availableSpotsResult = queryAvailableSpotsAcrossStations(
                intent.stationId(),
                intent.startTime().toString(),
                intent.endTime().toString(),
                intent.chargingType(),
                null,
                10);
        if (availableSpotsResult == null || !availableSpotsResult.startsWith("{")) {
            return AgentChatResponse.text(availableSpotsResult != null ? availableSpotsResult : "暂时无法查询可用车位。");
        }

        JSONObject result = JSON.parseObject(availableSpotsResult);
        JSONArray spots = result.getJSONArray("spots");
        if (spots == null || spots.isEmpty()) {
            String message = result.getString("message");
            return AgentChatResponse.text(message != null && !message.isBlank() ? message : "该时间段内没有符合条件的可用车位。");
        }

        JSONObject firstSpot = spots.getJSONObject(0);
        Long stationId = result.getLong("station_id");
        String stationName = result.getString("station_name");
        Long spotId = firstSpot.getLong("spot_id");

        RecommendBookingCardData cardData = RecommendBookingCardData.builder()
                .stationId(stationId)
                .stationName(stationName)
                .spotId(spotId)
                .spotCode(firstSpot.getString("车位编号"))
                .chargingType(firstSpot.getString("充电类型"))
                .startTime(intent.startTime().toString())
                .endTime(intent.endTime().toString())
                .pricePerHour(firstSpot.getBigDecimal("每小时价格"))
                .reason(buildRecommendationReason(intent.stationName(), stationName, firstSpot.getString("充电类型")))
                .build();

        boolean switchedStation = stationId != null
                && intent.stationId() != null
                && !stationId.equals(intent.stationId());
        if (switchedStation) {
            invalidatePendingReservation(intent.previousConfirmToken());
            return AgentChatResponse.of(
                    "recommend_booking",
                    buildCrossStationRecommendationMessage(intent.stationName(), cardData),
                    toRecommendBookingMap(cardData));
        }

        return createPendingReservationResponse(cardData, intent.previousConfirmToken());
    }

    private AgentChatResponse executeDeterministicStationDetail(PreparsedStationDetailIntent intent) {
        String detailResult = queryStationDetail(intent.stationId());
        if (detailResult == null || !detailResult.startsWith("{")) {
            return AgentChatResponse.text(detailResult != null ? detailResult : "暂时无法查询站点详情。");
        }

        String stationName = intent.stationName() != null && !intent.stationName().isBlank()
                ? intent.stationName()
                : "该充电站";
        return AgentChatResponse.of(
                "station_detail",
                "这是 " + stationName + " 的详情。告诉我开始时间、持续时长和快充/慢充偏好，我就能继续帮你预约。",
                JSON.parseObject(detailResult));
    }

    private String buildDeterministicReservationMessage(PreparsedReservationIntent intent, String stationName) {
        StringBuilder message = new StringBuilder("我已为你找到");
        if (intent.chargingType() != null) {
            message.append(displayChargingType(intent.chargingType()));
        } else {
            message.append("符合条件的");
        }
        message.append("车位");
        if (stationName != null && !stationName.isBlank()) {
            message.append("，站点是 ").append(stationName);
            if (!stationName.equals(intent.stationName())) {
                message.append("（原站点暂无符合条件车位，已顺延推荐）");
            }
        }
        message.append("。请确认预约信息，如需修改时段或车型偏好也可以直接告诉我。");
        return message.toString();
    }

    private String buildRecommendationReason(String requestedStationName, String stationName, String chargingType) {
        StringBuilder reason = new StringBuilder("基于您的需求推荐");
        if (chargingType != null && !chargingType.isBlank()) {
            reason.append(chargingType).append("车位");
        } else {
            reason.append("可用车位");
        }
        if (stationName != null && !stationName.isBlank()) {
            reason.append("，站点：").append(stationName);
        }
        if (requestedStationName != null
                && stationName != null
                && !stationName.isBlank()
                && !stationName.equals(requestedStationName)) {
            reason.append("（原站点暂无符合条件车位，已按距离顺延推荐）");
        }
        return reason.toString();
    }

    private String buildCrossStationRecommendationMessage(String requestedStationName, RecommendBookingCardData cardData) {
        String currentStationName = defaultText(requestedStationName, "当前站点");
        StringBuilder message = new StringBuilder(currentStationName);
        if (cardData.getChargingType() != null && !cardData.getChargingType().isBlank()) {
            message.append("当前没有可用的").append(cardData.getChargingType()).append("车位");
        } else {
            message.append("在该时间段没有可用车位");
        }
        message.append("，我已按距离顺延为你推荐");
        if (cardData.getStationName() != null && !cardData.getStationName().isBlank()) {
            message.append(cardData.getStationName());
        } else {
            message.append("下一个可预约站点");
        }
        message.append("。下面是 AI 推荐方案，点击可查看详情并继续预约。");
        return message.toString();
    }

    private AgentChatResponse createPendingReservationResponse(RecommendBookingCardData cardData,
                                                               String previousConfirmToken) {
        try {
            LocalDateTime startTime = LocalDateTime.parse(cardData.getStartTime(), DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime endTime = LocalDateTime.parse(cardData.getEndTime(), DateTimeFormatter.ISO_DATE_TIME);

            invalidatePendingReservation(previousConfirmToken);
            cleanupExpiredPendingReservations();
            String confirmToken = UUID.randomUUID().toString().replace("-", "");
            pendingReservations.put(confirmToken, new PendingReservation(
                    currentUserId.get(),
                    cardData.getSpotId(),
                    startTime,
                    endTime,
                    LocalDateTime.now(clock)));

            return AgentChatResponse.of(
                    "reservation_pending",
                    buildPendingReservationMessage(cardData),
                    toPendingReservationMap(confirmToken, cardData));
        } catch (Exception e) {
            log.warn("pending_reservation_prepare_failed | requestId={} | spotId={} | message={}",
                    currentRequestId.get(), cardData.getSpotId(), e.getMessage());
            return AgentChatResponse.of("recommend_booking", buildPendingReservationMessage(cardData), toRecommendBookingMap(cardData));
        }
    }

    private RecommendBookingCardData normalizeRecommendBookingCardData(RecommendBookingCardData cardData,
                                                                       String requestedStationName) {
        if (cardData == null) {
            return null;
        }
        return RecommendBookingCardData.builder()
                .stationId(cardData.getStationId())
                .stationName(cardData.getStationName())
                .spotId(cardData.getSpotId())
                .spotCode(cardData.getSpotCode())
                .chargingType(cardData.getChargingType())
                .startTime(cardData.getStartTime())
                .endTime(cardData.getEndTime())
                .pricePerHour(cardData.getPricePerHour())
                .reason(buildRecommendationReason(
                        requestedStationName,
                        cardData.getStationName(),
                        cardData.getChargingType()))
                .build();
    }

    private String buildPendingReservationMessage(RecommendBookingCardData cardData) {
        StringBuilder message = new StringBuilder("我已为你找到");
        if (cardData.getChargingType() != null && !cardData.getChargingType().isBlank()) {
            message.append(cardData.getChargingType());
        } else {
            message.append("符合条件的");
        }
        message.append("车位");
        if (cardData.getStationName() != null && !cardData.getStationName().isBlank()) {
            message.append("，站点是 ").append(cardData.getStationName());
        }
        message.append("。请确认预约信息，如需修改时段或车型偏好也可以直接告诉我。");
        return message.toString();
    }

    private Map<String, Object> toPendingReservationMap(String confirmToken, RecommendBookingCardData data) {
        Map<String, Object> result = new LinkedHashMap<>(toRecommendBookingMap(data));
        result.put("confirm_token", confirmToken);
        return result;
    }

    private String confirmReservation(String confirmToken) {
        if (confirmToken == null || confirmToken.isBlank()) {
            return "预约确认信息无效，请重新发起预约。";
        }

        PendingReservation pending = pendingReservations.get(confirmToken);
        if (pending == null) {
            return "预约确认已失效，请重新发起预约。";
        }
        if (isPendingReservationExpired(pending)) {
            pendingReservations.remove(confirmToken, pending);
            return "预约确认已过期，请重新发起预约。";
        }

        Long userId = currentUserId.get();
        if (userId == null) {
            return "请先登录后再确认预约。";
        }
        if (!userId.equals(pending.userId())) {
            return "该预约确认信息不属于当前用户。";
        }
        if (reservationService == null) {
            return "预约服务暂不可用，请稍后再试。";
        }

        try {
            reservationService.create(userId, ReservationRequest.builder()
                    .spotId(pending.spotId())
                    .startTime(pending.startTime())
                    .endTime(pending.endTime())
                    .build());
            pendingReservations.remove(confirmToken, pending);
            return "预约成功";
        } catch (Exception e) {
            String message = e.getMessage();
            return message != null && !message.isBlank() ? message : "预约失败，请稍后再试。";
        }
    }

    private void cleanupExpiredPendingReservations() {
        pendingReservations.entrySet().removeIf(entry -> isPendingReservationExpired(entry.getValue()));
    }

    private void invalidatePendingReservation(String confirmToken) {
        if (confirmToken == null || confirmToken.isBlank()) {
            return;
        }

        PendingReservation pending = pendingReservations.get(confirmToken);
        if (pending == null) {
            return;
        }

        Long userId = currentUserId.get();
        if (userId != null && !userId.equals(pending.userId())) {
            return;
        }
        pendingReservations.remove(confirmToken, pending);
    }

    private boolean isPendingReservationExpired(PendingReservation pending) {
        return pending != null
                && pending.createdAt() != null
                && pending.createdAt().plus(PENDING_RESERVATION_TTL).isBefore(LocalDateTime.now(clock));
    }

    private String buildReservationFollowUpFallback(String userQuestion, List<AgentHistoryMessage> history) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return null;
        }

        String normalized = normalizeQuestion(userQuestion);
        if (!containsAny(normalized, List.of("预约", "预定", "帮我约", "帮我预约"))) {
            return null;
        }
        if (parseTimeRange(userQuestion) != null) {
            return null;
        }
        if (containsAny(normalized, List.of("确认", "取消", "改约", "换一个", "如果", "要是", "否则", "或者", "优先"))) {
            return null;
        }

        StationSelection selection = resolveStationSelection(userQuestion, history);
        if (selection == null) {
            return null;
        }

        return "我已经锁定" + describeReservationFollowUpSelection(userQuestion, selection)
                + "了，请告诉我开始时间、持续多久，以及是否需要快充/慢充。";
    }

    private AgentChatResponse buildReservationFollowUpDegradedResponse(String userQuestion,
                                                                       List<AgentHistoryMessage> history,
                                                                       String reason) {
        String fallback = buildReservationFollowUpFallback(userQuestion, history);
        if (fallback == null) {
            return null;
        }
        log.info("reservation_followup_fallback | requestId={} | route={} | reason={} | failureType={}",
                currentRequestId.get(), currentRouteType.get(), reason, currentLlmFailureType.get());
        return AgentChatResponse.text(fallback);
    }

    private String describeReservationFollowUpSelection(String userQuestion, StationSelection selection) {
        Integer ordinal = extractOrdinal(userQuestion);
        if (ordinal != null) {
            return switch (ordinal) {
                case 1 -> "第一个充电站";
                case 2 -> "第二个充电站";
                case 3 -> "第三个充电站";
                default -> "你选择的充电站";
            };
        }

        if (selection != null && selection.stationName() != null && !selection.stationName().isBlank()) {
            return selection.stationName();
        }
        return "你选择的充电站";
    }

    private StationSelection resolveStationSelection(String userQuestion, List<AgentHistoryMessage> history) {
        StationContext context = findLatestStationContext(history);
        if (context != null && !context.stations().isEmpty()) {
            Integer ordinal = extractOrdinal(userQuestion);
            if (ordinal != null && ordinal > 0 && ordinal <= context.stations().size()) {
                ParsedStation station = context.stations().get(ordinal - 1);
                return new StationSelection(station.id(), station.name());
            }

            if (context.stations().size() == 1) {
                ParsedStation station = context.stations().get(0);
                return new StationSelection(station.id(), station.name());
            }

            String normalized = normalizeQuestion(userQuestion);
            if (containsAny(normalized, List.of("这个站", "该站", "这个充电站", "它"))) {
                if (context.stations().size() == 1) {
                    ParsedStation station = context.stations().get(0);
                    return new StationSelection(station.id(), station.name());
                }
            }
        }

        StationSelection lockedSelection = resolveLockedReservationSelection(history);
        if (lockedSelection != null) {
            return lockedSelection;
        }

        PendingReservationContext pendingContext = findLatestPendingReservationContext(history);
        if (pendingContext != null) {
            return new StationSelection(
                    pendingContext.stationId(),
                    resolveStationName(pendingContext.stationId(), history));
        }
        return null;
    }

    private SpotSelection resolveSpotSelection(String userQuestion, List<AgentHistoryMessage> history) {
        SpotContext context = findLatestSpotContext(history);
        if (context == null || context.spots().isEmpty()) {
            return null;
        }

        String normalized = normalizeQuestion(userQuestion);
        boolean mentionsSpot = containsAny(normalized, List.of(
                "车位", "桩位", "充电位", "停车位", "这个桩", "这个位", "就这个", "它"));
        Integer ordinal = extractOrdinal(userQuestion);

        ParsedSpot selectedSpot = null;
        if (ordinal != null
                && mentionsSpot
                && ordinal > 0
                && ordinal <= context.spots().size()) {
            selectedSpot = context.spots().get(ordinal - 1);
        } else if (context.spots().size() == 1
                && containsAny(normalized, List.of(
                "这个车位", "该车位", "就这个车位", "这个桩位", "该桩位",
                "这个充电位", "就这个", "这个桩", "这个位", "它"))) {
            selectedSpot = context.spots().get(0);
        }

        if (selectedSpot == null || parkingSpotService == null) {
            return null;
        }

        ParsedSpot resolvedSpot = selectedSpot;
        return parkingSpotService.findById(resolvedSpot.id())
                .map(spot -> {
                    Long stationId = null;
                    String stationName = null;
                    String chargingType = null;
                    if (spot.getPile() != null) {
                        chargingType = displayChargingType(spot.getPile().getPileType());
                        if (spot.getPile().getStation() != null) {
                            stationId = spot.getPile().getStation().getId();
                            stationName = spot.getPile().getStation().getName();
                        }
                    }
                    return new SpotSelection(resolvedSpot.id(), resolvedSpot.code(), stationId, stationName, chargingType);
                })
                .orElse(null);
    }

    private String resolveStationName(Long stationId, List<AgentHistoryMessage> history) {
        if (stationId == null) {
            return null;
        }

        StationContext context = findLatestStationContext(history);
        if (context != null) {
            for (ParsedStation station : context.stations()) {
                if (stationId.equals(station.id())) {
                    return station.name();
                }
            }
        }

        return stationService.findById(stationId)
                .map(ChargingStationDTO::getName)
                .orElse(null);
    }

    private SpotContext findLatestSpotContext(List<AgentHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            AgentHistoryMessage message = history.get(i);
            if (!"assistant".equals(message.getRole()) || message.getContent() == null) {
                continue;
            }
            String content = message.getContent();
            if (!content.contains("[可用车位]")) {
                continue;
            }

            List<ParsedSpot> spots = new ArrayList<>();
            Matcher matcher = AVAILABLE_SPOT_LIST_ITEM_PATTERN.matcher(content);
            while (matcher.find()) {
                spots.add(new ParsedSpot(
                        Long.parseLong(matcher.group(2)),
                        matcher.group(3).trim()));
            }
            if (!spots.isEmpty()) {
                return new SpotContext(spots);
            }
        }
        return null;
    }

    private StationContext findLatestStationContext(List<AgentHistoryMessage> history) {
        return findLatestStationContext(history, history != null ? history.size() : 0);
    }

    private StationContext findLatestStationContext(List<AgentHistoryMessage> history, int endExclusive) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = Math.min(endExclusive, history.size()) - 1; i >= 0; i--) {
            AgentHistoryMessage message = history.get(i);
            if (!"assistant".equals(message.getRole()) || message.getContent() == null) {
                continue;
            }
            String content = message.getContent();
            if (content.contains("[站点详情]")) {
                Matcher matcher = STATION_DETAIL_PATTERN.matcher(content);
                if (matcher.find()) {
                    return new StationContext(List.of(new ParsedStation(
                            Long.parseLong(matcher.group(1)),
                            matcher.group(2).trim())));
                }
            }
            if (content.contains("[站点数据]")) {
                List<ParsedStation> stations = new ArrayList<>();
                Matcher matcher = STATION_LIST_ITEM_PATTERN.matcher(content);
                while (matcher.find()) {
                    stations.add(new ParsedStation(
                            Long.parseLong(matcher.group(2)),
                            matcher.group(3).trim()));
                }
                if (!stations.isEmpty()) {
                    return new StationContext(stations);
                }
            }
        }
        return null;
    }

    private StationSelection resolveLockedReservationSelection(List<AgentHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            AgentHistoryMessage message = history.get(i);
            if (!"assistant".equals(message.getRole()) || message.getContent() == null) {
                continue;
            }

            String content = message.getContent();
            if (!content.contains("我已经锁定")) {
                StationSelection llmSelection = resolveStationSelectionFromPrompt(content, history, i);
                if (llmSelection != null) {
                    return llmSelection;
                }
                continue;
            }

            Integer ordinal = extractOrdinal(content);
            if (ordinal == null) {
                StationSelection llmSelection = resolveStationSelectionFromPrompt(content, history, i);
                if (llmSelection != null) {
                    return llmSelection;
                }
                continue;
            }

            StationContext earlierContext = findLatestStationContext(history, i);
            if (earlierContext == null || earlierContext.stations().size() < ordinal) {
                StationSelection llmSelection = resolveStationSelectionFromPrompt(content, history, i);
                if (llmSelection != null) {
                    return llmSelection;
                }
                continue;
            }

            ParsedStation station = earlierContext.stations().get(ordinal - 1);
            return new StationSelection(station.id(), station.name());
        }
        return null;
    }

    private StationSelection resolveStationSelectionFromPrompt(String content,
                                                               List<AgentHistoryMessage> history,
                                                               int currentIndex) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String normalized = normalizeQuestion(content);
        if (!containsAny(normalized, List.of(
                "站点id为", "查询可预约的车位", "请告诉我以下信息",
                "空闲车位信息", "更精准地推荐", "快充或慢充", "预约的时间段"))) {
            return null;
        }

        Matcher stationIdMatcher = LOCKED_STATION_ID_PATTERN.matcher(normalized);
        if (stationIdMatcher.find()) {
            Long stationId = Long.parseLong(stationIdMatcher.group(1));
            return new StationSelection(stationId, resolveStationName(stationId, history));
        }

        Integer ordinal = extractOrdinal(content);
        if (ordinal == null) {
            return null;
        }

        StationContext earlierContext = findLatestStationContext(history, currentIndex);
        if (earlierContext == null || earlierContext.stations().size() < ordinal) {
            return null;
        }

        ParsedStation station = earlierContext.stations().get(ordinal - 1);
        return new StationSelection(station.id(), station.name());
    }

    private PendingReservationContext findLatestPendingReservationContext(List<AgentHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            AgentHistoryMessage message = history.get(i);
            if (!"assistant".equals(message.getRole()) || message.getContent() == null) {
                continue;
            }

            String content = message.getContent();
            if (!content.contains("[待确认预约]")) {
                continue;
            }

            Long stationId = parseLong(extractPatternGroup(PENDING_STATION_ID_PATTERN, content));
            if (stationId == null) {
                continue;
            }

            List<LocalDateTime> times = extractExplicitDateTimes(content);
            if (times.size() < 2) {
                continue;
            }

            return new PendingReservationContext(
                    extractPatternGroup(PENDING_CONFIRM_TOKEN_PATTERN, content),
                    stationId,
                    parseLong(extractPatternGroup(PENDING_SPOT_ID_PATTERN, content)),
                    times.get(0).withSecond(0).withNano(0),
                    times.get(1).withSecond(0).withNano(0),
                    normalizeChargingType(extractPatternGroup(PENDING_CHARGING_TYPE_PATTERN, content)));
        }
        return null;
    }

    private String extractPatternGroup(Pattern pattern, String text) {
        if (pattern == null || text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isPendingReservationModification(String userQuestion, PendingReservationContext pendingContext) {
        if (pendingContext == null || userQuestion == null || userQuestion.isBlank()) {
            return false;
        }

        String normalized = normalizeQuestion(userQuestion);
        if (containsAny(normalized, List.of("确认", "取消", "支付", "签到", "签退", "订单"))) {
            return false;
        }

        return parseTimeRange(userQuestion) != null
                || extractReservationChargingType(userQuestion) != null
                || containsAny(normalized, List.of("改成", "改为", "换成", "调整", "修改"));
    }

    private Integer extractOrdinal(String userQuestion) {
        String normalized = normalizeQuestion(userQuestion);
        if (normalized.contains("第一个") || normalized.contains("第1个")) return 1;
        if (normalized.contains("第二个") || normalized.contains("第2个")) return 2;
        if (normalized.contains("第三个") || normalized.contains("第3个")) return 3;
        return null;
    }

    private TimeRange parseTimeRange(String userQuestion) {
        List<LocalDateTime> explicitTimes = extractExplicitDateTimes(userQuestion);
        if (explicitTimes.size() >= 2) {
            LocalDateTime start = explicitTimes.get(0).withSecond(0).withNano(0);
            LocalDateTime end = explicitTimes.get(1).withSecond(0).withNano(0);
            if (end.isAfter(start)) {
                return new TimeRange(start, end);
            }
        }

        String normalized = normalizeQuestion(userQuestion);
        if (containsAny(normalized, List.of("现在", "马上", "立刻"))) {
            Integer durationMinutes = parseDurationMinutes(userQuestion);
            if (durationMinutes != null && durationMinutes > 0) {
                LocalDateTime start = LocalDateTime.now(clock).withSecond(0).withNano(0);
                return new TimeRange(start, start.plusMinutes(durationMinutes));
            }
        }
        return null;
    }

    private List<LocalDateTime> extractExplicitDateTimes(String userQuestion) {
        Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?");
        Matcher matcher = pattern.matcher(userQuestion);
        List<LocalDateTime> values = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group().replace(' ', 'T');
            if (raw.length() == 16) {
                raw = raw + ":00";
            }
            try {
                values.add(LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME));
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private Integer parseDurationMinutes(String userQuestion) {
        Matcher hourMatcher = Pattern.compile("(\\d+)\\s*个?小时").matcher(userQuestion);
        if (hourMatcher.find()) {
            return Integer.parseInt(hourMatcher.group(1)) * 60;
        }

        Matcher minuteMatcher = Pattern.compile("(\\d+)\\s*分钟").matcher(userQuestion);
        if (minuteMatcher.find()) {
            return Integer.parseInt(minuteMatcher.group(1));
        }

        if (userQuestion.contains("半小时")) {
            return 30;
        }

        Map<String, Integer> chineseHours = Map.of(
                "一小时", 60,
                "两小时", 120,
                "二小时", 120,
                "三小时", 180,
                "四小时", 240,
                "五小时", 300,
                "六小时", 360);
        for (Map.Entry<String, Integer> entry : chineseHours.entrySet()) {
            if (userQuestion.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeQuestion(String userQuestion) {
        return userQuestion.replaceAll("\\s+", "")
                .replace('？', '?')
                .replace('！', '!')
                .replace('，', ',')
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, List<String> candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String summarizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private String buildSystemPrompt() {
        Long userId = currentUserId.get();
        String userStatus = userId != null
                ? "已登录（用户ID: " + userId + "，可以进行预约操作）"
                : "未登录（游客模式，不能进行预约操作，需提示用户先登录）";
        return AGENT_SYSTEM_PROMPT +
                "\n\n【当前上下文】\n" +
                "当前系统时间：" + LocalDateTime.now().format(SYSTEM_TIME_FORMATTER) + "\n" +
                "当前时区：" + TimeZone.getDefault().getID() + "\n" +
                "当前用户：" + userStatus;
    }

    private String normalizeChargingType(String chargingType) {
        if (chargingType == null || chargingType.isBlank()) {
            return null;
        }
        String normalized = chargingType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("快充") || "DC".equals(normalized) || normalized.contains("直流")) {
            return "DC";
        }
        if (normalized.contains("慢充") || "AC".equals(normalized) || normalized.contains("交流")) {
            return "AC";
        }
        return normalized;
    }

    private String normalizeReservationChargingType(String chargingType) {
        String normalized = normalizeChargingType(chargingType);
        return normalized != null ? normalized : DEFAULT_RESERVATION_CHARGING_TYPE;
    }

    private String displayChargingType(String pileType) {
        return switch (normalizeChargingType(pileType)) {
            case "DC" -> "直流快充";
            case "AC" -> "交流慢充";
            default -> pileType;
        };
    }

    // ==================== Tool 调用记录 ====================

    private record ToolRecord(String name, String arguments, String result) {}

    private record CrossStationBookingContext(boolean switchedStation, String requestedStationName) {}

    private record SimpleStationQuery(boolean matched, String keyword, String chargingType) {
        private static SimpleStationQuery notMatched() {
            return new SimpleStationQuery(false, null, null);
        }
    }

    private record PreparsedReservationIntent(boolean matched, Long stationId, String stationName,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              String chargingType, Long spotId, String previousConfirmToken) {
        private static PreparsedReservationIntent notMatched() {
            return new PreparsedReservationIntent(false, null, null, null, null, null, null, null);
        }
    }

    private record PreparsedStationDetailIntent(boolean matched, Long stationId, String stationName) {
        private static PreparsedStationDetailIntent notMatched() {
            return new PreparsedStationDetailIntent(false, null, null);
        }
    }

    private record ParsedStation(Long id, String name) {}

    private record StationContext(List<ParsedStation> stations) {}

    private record ParsedSpot(Long id, String code) {}

    private record SpotContext(List<ParsedSpot> spots) {}

    private record StationSelection(Long stationId, String stationName) {}

    private record SpotSelection(Long spotId, String spotCode, Long stationId, String stationName,
                                 String chargingType) {}

    private record TimeRange(LocalDateTime startTime, LocalDateTime endTime) {}

    private record PendingReservationContext(String confirmToken, Long stationId, Long spotId,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             String chargingType) {}

    private record PendingReservation(Long userId, Long spotId, LocalDateTime startTime,
                                      LocalDateTime endTime, LocalDateTime createdAt) {}

    private enum LlmFailureType {
        NONE,
        TIMEOUT,
        CONNECTION,
        DNS,
        TLS,
        HTTP_401,
        HTTP_403,
        HTTP_4XX,
        HTTP_5XX,
        CONFIGURATION,
        INTERRUPTED,
        UNKNOWN
    }

    // ==================== LLM 调用 ====================

    private JSONObject callLlmWithTools(List<Map<String, Object>> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 4096);
        requestBody.put("tools", buildToolDefinitions());
        requestBody.put("tool_choice", "auto");
        return sendRequest(requestBody);
    }

    private JSONObject sendRequest(Map<String, Object> requestBody) {
        if (apiUrl == null || apiUrl.isBlank()) {
            currentLlmFailureType.set(LlmFailureType.CONFIGURATION);
            log.warn("llm_request_failed | requestId={} | route={} | failureType={} | reason=missing_api_url",
                    currentRequestId.get(), currentRouteType.get(), currentLlmFailureType.get());
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            currentLlmFailureType.set(LlmFailureType.CONFIGURATION);
            log.warn("llm_request_failed | requestId={} | route={} | failureType={} | reason=missing_api_key",
                    currentRequestId.get(), currentRouteType.get(), currentLlmFailureType.get());
            return null;
        }

        String jsonBody = JSON.toJSONString(requestBody);
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startNanos = System.nanoTime();
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey);
                if (agentTimeoutSeconds > 0) {
                    requestBuilder.timeout(Duration.ofSeconds(agentTimeoutSeconds));
                }
                HttpRequest request = requestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                log.info("llm_request_start | requestId={} | attempt={} | route={} | model={} | timeoutSeconds={} | historyCount={} | question={}",
                        currentRequestId.get(),
                        attempt,
                        currentRouteType.get(),
                        modelName,
                        agentTimeoutSeconds,
                        currentHistoryCount.get(),
                        currentQuestionSummary.get());

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

                if (response.statusCode() == 200) {
                    currentLlmFailureType.set(LlmFailureType.NONE);
                    log.info("llm_request_end | requestId={} | attempt={} | status={} | durationMs={} | route={}",
                            currentRequestId.get(), attempt, response.statusCode(), durationMs, currentRouteType.get());
                    return JSON.parseObject(response.body());
                }

                currentLlmFailureType.set(classifyHttpStatus(response.statusCode()));
                log.warn("llm_request_failed | requestId={} | attempt={} | status={} | durationMs={} | route={} | failureType={} | body={}",
                        currentRequestId.get(),
                        attempt,
                        response.statusCode(),
                        durationMs,
                        currentRouteType.get(),
                        currentLlmFailureType.get(),
                        abbreviateResponseBody(response.body()));
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                currentLlmFailureType.set(LlmFailureType.INTERRUPTED);
                log.warn("llm_request_failed | requestId={} | attempt={} | route={} | failureType={} | message={}",
                        currentRequestId.get(), attempt, currentRouteType.get(), currentLlmFailureType.get(), e.getMessage());
                break;
            } catch (Exception e) {
                long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                LlmFailureType failureType = classifyLlmException(e);
                currentLlmFailureType.set(failureType);
                log.error("llm_request_failed | requestId={} | attempt={} | route={} | failureType={} | durationMs={} | message={}",
                        currentRequestId.get(),
                        attempt,
                        currentRouteType.get(),
                        failureType,
                        durationMs,
                        e.getMessage(),
                        e);
                if (shouldRetryLlmRequest(attempt, maxAttempts, failureType)) {
                    log.info("llm_request_retry | requestId={} | nextAttempt={} | failureType={}",
                            currentRequestId.get(), attempt + 1, failureType);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        currentLlmFailureType.set(LlmFailureType.INTERRUPTED);
                        break;
                    }
                    continue;
                }
                break;
            }
        }
        return null;
    }

    private boolean shouldRetryLlmRequest(int attempt, int maxAttempts, LlmFailureType failureType) {
        if (attempt >= maxAttempts) {
            return false;
        }
        if (failureType == LlmFailureType.TIMEOUT) {
            // 超时后立即走业务兜底，避免总耗时超过前端请求上限。
            return false;
        }
        return isRetryableNetworkFailure(failureType);
    }

    private String buildModelUnavailableMessage(LlmFailureType failureType) {
        return switch (failureType != null ? failureType : LlmFailureType.UNKNOWN) {
            case TIMEOUT -> "抱歉，模型服务响应超时，请稍后再试。";
            case CONNECTION, DNS, TLS -> "抱歉，模型服务网络连接异常，请稍后再试。";
            case HTTP_401, HTTP_403 -> "抱歉，模型服务鉴权失败，请联系管理员检查配置。";
            case HTTP_4XX -> "抱歉，模型服务请求异常，请稍后再试。";
            case HTTP_5XX -> "抱歉，模型服务暂时不可用，请稍后再试。";
            case CONFIGURATION -> "抱歉，模型服务配置缺失，请联系管理员检查配置。";
            case INTERRUPTED -> "抱歉，模型请求被中断，请稍后再试。";
            default -> "抱歉，模型服务暂时不可用，请稍后再试。";
        };
    }

    private LlmFailureType classifyHttpStatus(int statusCode) {
        if (statusCode == 401) {
            return LlmFailureType.HTTP_401;
        }
        if (statusCode == 403) {
            return LlmFailureType.HTTP_403;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return LlmFailureType.HTTP_4XX;
        }
        if (statusCode >= 500) {
            return LlmFailureType.HTTP_5XX;
        }
        return LlmFailureType.UNKNOWN;
    }

    private LlmFailureType classifyLlmException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (throwable instanceof HttpTimeoutException || cause instanceof HttpTimeoutException) {
            return LlmFailureType.TIMEOUT;
        }
        if (throwable instanceof UnknownHostException || cause instanceof UnknownHostException) {
            return LlmFailureType.DNS;
        }
        if (throwable instanceof ConnectException || cause instanceof ConnectException) {
            return LlmFailureType.CONNECTION;
        }
        if (throwable instanceof SSLException || cause instanceof SSLException) {
            return LlmFailureType.TLS;
        }
        return LlmFailureType.UNKNOWN;
    }

    private boolean isRetryableNetworkFailure(LlmFailureType failureType) {
        return failureType == LlmFailureType.TIMEOUT
                || failureType == LlmFailureType.CONNECTION
                || failureType == LlmFailureType.DNS
                || failureType == LlmFailureType.TLS;
    }

    private String abbreviateResponseBody(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    // ==================== 工具定义 ====================

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(buildTool("query_all_stations",
                "查询系统中当前有空闲充电桩的充电站列表，按距离从近到远排序，已过滤掉全部桩都在使用中的站点",
                Map.of("type", "object", "properties", Map.of(), "required", List.of())));

        tools.add(buildTool("query_station_by_keyword",
                "按关键词搜索充电站，可以搜索充电站名称、地址或城市",
                Map.of("type", "object", "properties",
                        Map.of("keyword", Map.of("type", "string", "description", "搜索关键词")),
                        "required", List.of("keyword"))));

        tools.add(buildTool("query_station_detail",
                "查询指定充电站的详细信息，包括每根充电桩的编号、类型、功率、状态和车位列表",
                Map.of("type", "object", "properties",
                        Map.of("station_id", Map.of("type", "integer", "description", "充电站的ID编号")),
                        "required", List.of("station_id"))));

        tools.add(buildTool("query_available_spots",
                "查询指定充电站在指定时间段内可预约的空闲车位，可按快充或慢充筛选，返回车位ID、编号、类型和价格",
                Map.of("type", "object", "properties",
                        Map.of(
                                "station_id", Map.of("type", "integer", "description", "充电站ID"),
                                "start_time", Map.of("type", "string", "description", "预约开始时间，ISO格式如2026-04-08T15:00:00"),
                                "end_time", Map.of("type", "string", "description", "预约结束时间，ISO格式如2026-04-08T16:00:00"),
                                "charging_type", Map.of("type", "string", "description", "可选，充电类型偏好：快充/慢充/DC/AC")),
                        "required", List.of("station_id", "start_time", "end_time"))));

        tools.add(buildTool("query_available_spots_across_stations",
                "按推荐顺序跨站点查询指定时间段内可预约的空闲车位。当用户指定的站点没有快充或慢充车位时，先检查该站点，再继续检查后续推荐站点，直到找到符合条件的车位或候选站点全部查完。适用于用户说'如果这个站没有快充/慢充就换下一个'、'没有就顺延推荐'等场景",
                Map.of("type", "object", "properties",
                        Map.of(
                                "station_id", Map.of("type", "integer", "description", "可选，用户优先指定的充电站ID；提供后会优先检查该站点"),
                                "start_time", Map.of("type", "string", "description", "预约开始时间，ISO格式如2026-04-08T15:00:00"),
                                "end_time", Map.of("type", "string", "description", "预约结束时间，ISO格式如2026-04-08T16:00:00"),
                                "charging_type", Map.of("type", "string", "description", "可选，充电类型偏好：快充/慢充/DC/AC"),
                                "keyword", Map.of("type", "string", "description", "可选，站点关键词或城市关键词，用于限定候选站点"),
                                "max_stations", Map.of("type", "integer", "description", "可选，最多检查的候选站点数量，默认10")),
                        "required", List.of("start_time", "end_time"))));

        tools.add(buildTool("recommend_booking",
                "推荐预约方案：校验参数并返回推荐的预约信息（站点、车位、时间、价格），不创建预约。系统会引导用户到确认页面完成预约",
                Map.of("type", "object", "properties",
                        Map.of(
                                "spot_id", Map.of("type", "integer", "description", "推荐的车位ID"),
                                "start_time", Map.of("type", "string", "description", "推荐开始时间，ISO格式如2026-04-08T15:00:00"),
                                "end_time", Map.of("type", "string", "description", "推荐结束时间，ISO格式如2026-04-08T16:00:00")),
                        "required", List.of("spot_id", "start_time", "end_time"))));

        tools.add(buildTool("query_user_reservations",
                "查询当前登录用户的预约记录列表，包括预约的站点、车位、时间和状态",
                Map.of("type", "object", "properties", Map.of(), "required", List.of())));

        return tools;
    }

    private Map<String, Object> buildTool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", Map.of("name", name, "description", description, "parameters", parameters));
        return tool;
    }

    // ==================== RAG 知识检索 ====================

    /**
     * 调用 RAG 知识库检索与用户问题相关的知识片段
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
            log.warn("Agent RAG 检索失败，降级为无知识库模式", e);
            return "";
        }
    }
}
