package com.n4d3sh1k4.solution_archive_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Запрос на решение прямое решение уравнения ")
public class SolveRequestDto {
    @Schema(description = "Уравнение от пользователя в формате LaTeX", example = "x^{2}-2x-3=0")
    @NotNull
    private String equation;
}