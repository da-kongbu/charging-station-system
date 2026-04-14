package com.charging.controller;

import com.charging.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiChatController controller = new AiChatController(null, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAgentShouldReturnMethodNotAllowedInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/api/ai/agent"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法 'GET' 不支持，支持的方法: [POST]"));
    }

    @Test
    void postAgentShouldValidateDtoRequestBody() throws Exception {
        mockMvc.perform(post("/api/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "   ",
                                  "history": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("question 不能为空"))
                .andExpect(jsonPath("$.data.question").value("question 不能为空"));
    }

    @Test
    void confirmReservationShouldValidateDtoRequestBody() throws Exception {
        mockMvc.perform(post("/api/ai/agent/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmToken": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("confirmToken 不能为空"))
                .andExpect(jsonPath("$.data.confirmToken").value("confirmToken 不能为空"));
    }
}
