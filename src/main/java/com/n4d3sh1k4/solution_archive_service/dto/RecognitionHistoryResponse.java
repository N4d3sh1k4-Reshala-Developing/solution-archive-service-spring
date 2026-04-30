package com.n4d3sh1k4.solution_archive_service.dto;

import com.n4d3sh1k4.solution_archive_service.model.RecognitionStatus;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecognitionHistoryResponse {
    private String id;
    private String editedResult;
    private LocalDateTime createdAt;
    private String originalResult;
    private String solutionResult;
    private RecognitionStatus status;

    public static RecognitionHistoryResponse fromEntity(RecognitionTask task) {
        return RecognitionHistoryResponse.builder()
                .id(task.getId())
                .editedResult(task.getEditedResult())
                .createdAt(task.getCreatedAt())
                .originalResult(task.getOriginalResult())
                .solutionResult(task.getSolutionResult())
                .status(task.getStatus())
                .build();
    }
}
