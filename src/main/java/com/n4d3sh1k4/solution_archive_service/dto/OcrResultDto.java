package com.n4d3sh1k4.solution_archive_service.dto;

import lombok.Data;

@Data
public class OcrResultDto {
    private String taskId; // latexOCR internal task id
    private String status; // SUCCESS or FAILURE
    private String result; // Recognized latex string
    private String error;
}
