package com.n4d3sh1k4.solution_archive_service.dto;

import com.n4d3sh1k4.solution_archive_service.model.RecognitionStatus;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Ответ с результатами задачи распознавания")
public class RecognitionResponse {
    @Schema(description = "Уникальный идентификатор задачи", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;
    
    @Schema(description = "Текущий статус задачи", example = "COMPLETED")
    private RecognitionStatus status;
    
    @Schema(description = "Оригинальный результат OCR", example = "x^2 + y^2 = z^2")
    private String originalResult;
    
    @Schema(description = "Откорректированный пользователем результат", example = "x^2 + y^2 = c^2")
    private String editedResult;
    
    @Schema(description = "Конечное решение (ответ CASengine)", example = "x = ...")
    private String solutionResult;
    
    @Schema(description = "Время создания задачи")
    private LocalDateTime createdAt;
    
    @Schema(description = "Крайний срок для отправки фидбека (корректировки)")
    private LocalDateTime feedbackDeadline;

    public static RecognitionResponse fromEntity(RecognitionTask task) {
        if (task == null) return null;
        return RecognitionResponse.builder()
                .id(task.getId())
                .status(task.getStatus())
                .originalResult(task.getOriginalResult())
                .editedResult(task.getEditedResult())
                .solutionResult(task.getSolutionResult())
                .createdAt(task.getCreatedAt())
                .feedbackDeadline(task.getFeedbackDeadline())
                .build();
    }
}
