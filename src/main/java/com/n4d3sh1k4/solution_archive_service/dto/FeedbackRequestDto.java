package com.n4d3sh1k4.solution_archive_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Запрос на отправку откорректированного результата (фидбека)")
public class FeedbackRequestDto {
    @Schema(description = "Откорректированный пользователем LaTeX код", example = "\\frac{1}{2}")
    private String editedResult;
}
