package com.charging.dto.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    @DecimalMin(value = "-90.0", message = "lat 不能小于 -90")
    @DecimalMax(value = "90.0", message = "lat 不能大于 90")
    private Double lat;

    @DecimalMin(value = "-180.0", message = "lng 不能小于 -180")
    @DecimalMax(value = "180.0", message = "lng 不能大于 180")
    private Double lng;

    @Valid
    @Size(max = 20, message = "history 最多保留 20 条消息")
    @Builder.Default
    private List<AgentHistoryMessage> history = new ArrayList<>();
}
