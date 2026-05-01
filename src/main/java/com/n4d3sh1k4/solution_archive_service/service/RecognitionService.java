package com.n4d3sh1k4.solution_archive_service.service;

import com.n4d3sh1k4.solution_archive_service.config.RabbitMQConfig;
import com.n4d3sh1k4.solution_archive_service.dto.FeedbackRequestDto;
import com.n4d3sh1k4.solution_archive_service.dto.OcrResultDto;
import com.n4d3sh1k4.solution_archive_service.model.DatasetEntry;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionStatus;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import com.n4d3sh1k4.solution_archive_service.repository.DatasetEntryRepository;
import com.n4d3sh1k4.solution_archive_service.repository.RecognitionTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecognitionService {

    private final RecognitionTaskRepository taskRepository;
    private final DatasetEntryRepository  datasetEntryRepository;
    private final MinioService minioService;
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${casengine.url}")
    private String casEngineUrl;
    
    @Value("${latexocr.url}")
    private String latexOcrUrl;

    @Transactional
    public RecognitionTask initiateRecognition(MultipartFile file, java.util.UUID userId) {
        String tempImagePath = minioService.saveToTempBucket(file);

        RecognitionTask task = RecognitionTask.builder()
                .userId(userId)
                .status(RecognitionStatus.RECOGNIZING)
                .imagePath(tempImagePath)
                .build();
        task = taskRepository.save(task);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(latexOcrUrl + "/api/v1/ocr", requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String celeryTaskId = (String) response.getBody().get("task_id");
                task.setLatexOcrTaskId(celeryTaskId);
                taskRepository.save(task);
                log.info("Sent task to latexOCR, Celery task_id: {}, Local ID: {}", celeryTaskId, task.getId());
            } else {
                markTaskAsFailed(task, "LatexOCR HTTP Response " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Failed to send image to LatexOCR", e);
            markTaskAsFailed(task, e.getMessage());
        }

        return task;
    }

    private void markTaskAsFailed(RecognitionTask task, String error) {
        task.setStatus(RecognitionStatus.FAILED);
        task.setOriginalResult("Error: " + error);
        if (task.getImagePath() != null) {
            minioService.deleteFromTempBucket(task.getImagePath());
            task.setImagePath(null);
        }
        taskRepository.save(task);
    }

    @Transactional
    public void processOcrResult(OcrResultDto dto) {
        Optional<RecognitionTask> optionalTask = taskRepository.findByLatexOcrTaskId(dto.getTaskId());
        if (optionalTask.isEmpty()) {
            log.warn("Received OCR result for unknown celery task_id: {}", dto.getTaskId());
            return;
        }

        RecognitionTask task = optionalTask.get();
        if ("SUCCESS".equals(dto.getStatus())) {
            task.setStatus(RecognitionStatus.READY_FOR_FEEDBACK);
            task.setOriginalResult(dto.getResult());
            task.setFeedbackDeadline(LocalDateTime.now().plusMinutes(30));
            taskRepository.save(task);

            rabbitTemplate.convertAndSend(RabbitMQConfig.FEEDBACK_DELAY_EXCHANGE, "", task.getId());
            log.info("Task {} is READY_FOR_FEEDBACK. Delayed 30 min check scheduled.", task.getId());
        } else {
            markTaskAsFailed(task, dto.getError());
        }
    }

    @Transactional
    public RecognitionTask handleUserFeedback(String taskId, FeedbackRequestDto feedbackRequestDto) {
        RecognitionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getStatus() != RecognitionStatus.READY_FOR_FEEDBACK) {
            throw new RuntimeException("Task is not awaiting feedback");
        }

        task.setStatus(RecognitionStatus.COMPLETED_EDITED);
        task.setEditedResult(feedbackRequestDto.getEditStatus() ? feedbackRequestDto.getEditedResult() : null);
        task = taskRepository.save(task);
        sendToCasEngine(task);

        return task;
    }

    @Transactional
    public void handleFeedbackTimeout(String taskId) {
        Optional<RecognitionTask> optionalTask = taskRepository.findById(taskId);
        if (optionalTask.isEmpty()) return;
        
        RecognitionTask task = optionalTask.get();
        
        // Only accept logic if it's still waiting. It might have been edited by user in meantime.
        if (task.getStatus() == RecognitionStatus.READY_FOR_FEEDBACK) {
            log.info("Feedback timeout reached for task {}. Auto-completing.", taskId);
            task.setStatus(RecognitionStatus.COMPLETED_AUTO);
            
            // Delete temp file because we don't save auto-completed ones for ML
            if (task.getImagePath() != null) {
                minioService.deleteFromTempBucket(task.getImagePath());
                task.setImagePath(null);
            }
            
            task = taskRepository.save(task);
            
            sendToCasEngine(task);
        }
    }
    
    public RecognitionTask getTask(String taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    private void sendToCasEngine(RecognitionTask task) {
        try {
            String equation = task.getEditedResult() != null ? task.getEditedResult() : task.getOriginalResult();
            if (equation == null || equation.isBlank()) {
                log.warn("No equation found to send to CAS engine for task {}", task.getId());
                return;
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of("equation", equation);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(casEngineUrl + "/solve", requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String casTaskId = (String) response.getBody().get("task_id");
                task.setCasEngineTaskId(casTaskId);
                task.setStatus(RecognitionStatus.SOLVING_EQUATION);
                taskRepository.save(task);
                log.info("Sent equation to CAS Engine, CAS task_id: {}, Local ID: {}", casTaskId, task.getId());
            } else {
                markTaskAsFailed(task, "CAS Engine HTTP Response " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Failed to send equation to CAS engine", e);
            markTaskAsFailed(task, "CAS Engine failed: " + e.getMessage());
        }
    }

    @Transactional
    public void processCasResult(com.n4d3sh1k4.solution_archive_service.dto.CasResultDto dto) {
        taskRepository.findAll().stream()
                .filter(t -> dto.getTaskId().equals(t.getCasEngineTaskId()))
                .findFirst()
                .ifPresentOrElse(task -> {
                    if ("SUCCESS".equals(dto.getStatus())) {
                        try {
                            task.setSolutionResult(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto.getResult()));
                            task.setStatus(RecognitionStatus.SOLUTION_READY);
                            taskRepository.save(task);
                            if(task.getImagePath() != null && task.getEditedResult() != null) {
                                minioService.moveToDatasetBucket(task.getImagePath());
                                log.info("User edited result for {}, image moved to dataset", task.getId());
                                DatasetEntry data = DatasetEntry.builder()
                                        .originalTaskId(task.getId())
                                        .latexContent(task.getEditedResult())
                                        .imagePath(task.getImagePath())
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                datasetEntryRepository.save(data);
                            }
                            log.info("Solution received for task {}", task.getId());
                        } catch (Exception e) {
                            markTaskAsFailed(task, "Failed to serialize solution");
                        }
                    } else {
                        markTaskAsFailed(task, dto.getError());
                    }
                }, () -> log.warn("Received CAS result for unknown task_id: {}", dto.getTaskId()));
    }
}
