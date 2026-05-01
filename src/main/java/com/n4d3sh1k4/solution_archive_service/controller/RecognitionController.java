package com.n4d3sh1k4.solution_archive_service.controller;

import com.n4d3sh1k4.common.exception.ContentNotFoundException;
import com.n4d3sh1k4.common.exception.UniversalExeption;
import com.n4d3sh1k4.solution_archive_service.dto.FeedbackRequestDto;
import com.n4d3sh1k4.solution_archive_service.dto.RecognitionResponse;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import com.n4d3sh1k4.solution_archive_service.service.RecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Распознавание", description = "API для загрузки изображений и получения результатов распознавания и вычислений")
@RestController
@RequestMapping("/recognition")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    @Operation(summary = "Отправить изображение на распознавание", description = "Загружает изображение с формулой. Возвращает статус ACCEPTED и базовую информацию по задаче.")
    @PostMapping(value = "/process", consumes = {"multipart/form-data"})
    public ResponseEntity<RecognitionResponse> processImage(
            @Parameter(description = "Файл изображения") @RequestParam("file") MultipartFile file,
            @Parameter(description = "ID пользователя (передается через Gateway)") @RequestHeader(value = "X-User-Id", required = false) java.util.UUID userId) {
        if (file.isEmpty()) {
            throw new UniversalExeption("File is empty", "FILE_EMPTY", HttpStatus.BAD_REQUEST);
        }
        RecognitionTask task = recognitionService.initiateRecognition(file, userId);
        return ResponseEntity.accepted().body(RecognitionResponse.fromEntity(task));
    }

    @Operation(summary = "Получить статус и результат задачи", description = "Возвращает текущее состояние задачи распознавания по её ID.")
    @GetMapping("/{id}")
    public RecognitionResponse getTask(@Parameter(description = "ID задачи распознавания") @PathVariable String id) {
        RecognitionTask task = recognitionService.getTask(id);
        if (task == null) {
            throw new ContentNotFoundException("Task not found");
        }
        return RecognitionResponse.fromEntity(task);
    }

    @Operation(summary = "Отправить корректировку (фидбек)", description = "Позволяет пользователю прислать исправленный LaTeX-код, если OCR ошибся. Это перезапустит процесс решения (CASengine).")
    @PostMapping("/{id}/feedback")
    public RecognitionResponse submitFeedback(
            @Parameter(description = "ID задачи распознавания") @PathVariable String id, 
            @Parameter(description = "Объект с исправленным LaTeX") @RequestBody FeedbackRequestDto dto) {
        try {
            RecognitionTask task = recognitionService.handleUserFeedback(id, dto);
            return RecognitionResponse.fromEntity(task);
        } catch (RuntimeException e) {
            throw new UniversalExeption(e.getMessage(), "BAD_REQUEST", HttpStatus.BAD_REQUEST);
        }
    }
}