package com.n4d3sh1k4.solution_archive_service.dto;

import lombok.Data;

@Data
public class CasResultDto {
    private String taskId;
    private String status;
    private Object result; // It's an array of SolutionStep but we can store it as JSON or object
    private String error;
}
