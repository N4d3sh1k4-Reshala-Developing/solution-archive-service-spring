package com.n4d3sh1k4.solution_archive_service.dto;

import com.n4d3sh1k4.solution_archive_service.model.UserStatistics;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Ответ с результатами задачи распознавания")
public class UserStatisticsResponse {
    private long totalTasks;
    private long successTasks;
    private long errorTasks;
    private long editedTasks;
    private long directSolutionTasks;

    public static UserStatisticsResponse fromEntity(UserStatistics userStatistics) {
        if (userStatistics == null) return null;
        return UserStatisticsResponse.builder()
                .totalTasks(userStatistics.getTotalTasks())
                .successTasks(userStatistics.getSuccessTasks())
                .errorTasks(userStatistics.getErrorTasks())
                .editedTasks(userStatistics.getEditedTasks())
                .directSolutionTasks(userStatistics.getDirectSolutionTasks())
                .build();
    }
}
