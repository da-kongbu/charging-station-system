package com.charging.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHistoryMessage {

    @NotBlank(message = "history.role 不能为空")
    @Pattern(regexp = "user|assistant|system|tool", message = "history.role 仅支持 user/assistant/system/tool")
    private String role;

    @NotBlank(message = "history.content 不能为空")
    private String content;
}
