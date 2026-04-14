package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.dto.agent.AgentChatRequest;
import com.charging.dto.agent.AgentChatResponse;
import com.charging.dto.agent.ConfirmReservationRequest;
import com.charging.security.JwtTokenProvider;
import com.charging.service.AgentService;
import com.charging.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * AI 智能体控制器
 *
 * 统一入口：POST /api/ai/agent
 * Agent 基于 Function Calling 自主决策是否调用工具和检索知识库，
 * 无需维护多个独立 AI 通道。
 */
@RestController
@RequestMapping(value = "/api/ai", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final AgentService agentService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Agent 智能体接口（Function Calling，支持多轮对话）
     * 前端 POST 发送当前问题 + 对话历史，AI 拥有上下文记忆
     */
    @PostMapping("/agent")
    public ResponseEntity<ApiResponse<AgentChatResponse>> agent(
            @Valid @RequestBody AgentChatRequest body,
            HttpServletRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails, request);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        log.info("agent_request_received | requestId={} | historyCount={} | userId={} | question={}",
                requestId,
                body.getHistory() != null ? body.getHistory().size() : 0,
                userId,
                abbreviateQuestion(body.getQuestion()));
        AgentChatResponse answer = agentService.agentChat(body, userId, requestId);
        return ResponseEntity.ok(ApiResponse.success("Agent 回复成功", answer));
    }

    @PostMapping("/agent/reservations/confirm")
    public ResponseEntity<ApiResponse<String>> confirmReservation(
            @Valid @RequestBody ConfirmReservationRequest body,
            HttpServletRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails, request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.unauthorized("请先登录后再确认预约"));
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String message = agentService.confirmPendingReservation(body.getConfirmToken(), userId, requestId);
        if ("预约成功".equals(message)) {
            return ResponseEntity.ok(ApiResponse.success("预约成功", message));
        }

        HttpStatus status = message != null && message.contains("不属于当前用户")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), message != null ? message : "预约失败"));
    }

    private String abbreviateQuestion(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private Long resolveUserId(UserDetails userDetails, HttpServletRequest request) {
        if (userDetails != null) {
            return userService.findByUsername(userDetails.getUsername())
                    .map(u -> u.getId())
                    .orElse(null);
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        return userService.findByUsername(username)
                .map(u -> u.getId())
                .orElse(null);
    }
}
