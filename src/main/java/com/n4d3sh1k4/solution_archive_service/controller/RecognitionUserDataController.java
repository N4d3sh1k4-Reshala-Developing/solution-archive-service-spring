package com.n4d3sh1k4.solution_archive_service.controller;

import com.n4d3sh1k4.solution_archive_service.dto.RecognitionHistoryResponse;
import com.n4d3sh1k4.solution_archive_service.service.RecognitionUserDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "RD Recognition/Solve user results", description = "API для получения результатов распознавания и вычислений пользователя")
@RestController
@RequestMapping("/recognition/data")
@RequiredArgsConstructor
@Slf4j
public class RecognitionUserDataController {
    private final RecognitionUserDataService recognitionService;

    @Operation(
        summary = "Получить историю пользователя",
        description = "Возвращает список всех задач распознавания для конкретного пользователя."
    )
    @GetMapping("/history")
    public ResponseEntity<List<RecognitionHistoryResponse>> getHistory(
            @Parameter(description = "ID пользователя (из Gateway)")
            @RequestHeader(value = "X-User-Id") UUID userId) {
        List<RecognitionHistoryResponse> history = recognitionService.getUserHistory(userId);
        log.info("History: {}", history);
        return ResponseEntity.ok(history);
    }
}
