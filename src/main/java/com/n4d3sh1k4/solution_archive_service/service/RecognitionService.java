package com.n4d3sh1k4.solution_archive_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n4d3sh1k4.solution_archive_service.config.RabbitMQConfig;
import com.n4d3sh1k4.solution_archive_service.dto.FeedbackRequestDto;
import com.n4d3sh1k4.solution_archive_service.dto.OcrResultDto;
import com.n4d3sh1k4.solution_archive_service.dto.SolveRequestDto;
import com.n4d3sh1k4.solution_archive_service.model.DatasetEntry;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionStatus;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import com.n4d3sh1k4.solution_archive_service.repository.DatasetEntryRepository;
import com.n4d3sh1k4.solution_archive_service.repository.RecognitionTaskRepository;
import com.n4d3sh1k4.solution_archive_service.repository.UserStatisticsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RecognitionService {
    private final RecognitionTaskRepository taskRepository;
    private final DatasetEntryRepository datasetEntryRepository;
    private final UserStatisticsRepository userStatisticsRepository;
    private final MinioService minioService;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RecognitionService(RecognitionTaskRepository taskRepository,
                              DatasetEntryRepository datasetEntryRepository,
                              UserStatisticsRepository userStatisticsRepository,
                              MinioService minioService,
                              RabbitTemplate rabbitTemplate,
                              PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.datasetEntryRepository = datasetEntryRepository;
        this.userStatisticsRepository = userStatisticsRepository;
        this.minioService = minioService;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Value("${casengine.url}")
    private String casEngineUrl;

    @Value("${latexocr.url}")
    private String latexOcrUrl;


    public RecognitionTask initiateRecognition(MultipartFile file, java.util.UUID userId) {
        log.info("Initiating recognition for user: {}", userId);
        
        String tempImagePath;
        try {
            tempImagePath = minioService.saveToTempBucket(file);
        } catch (Exception e) {
            log.error("Failed to save image to MinIO", e);
            throw new RuntimeException("Storage error: " + e.getMessage());
        }

        RecognitionTask task;
        try {
            final String path = tempImagePath;
            task = transactionTemplate.execute(status -> {
                log.debug("Saving initial task in transaction");
                return saveInitialTask(path, userId);
            });
        } catch (Exception e) {
            log.error("Failed to save initial task", e);
            throw new RuntimeException("Database error: " + e.getMessage());
        }

        if (task == null) {
            log.error("Task creation returned null");
            throw new RuntimeException("Failed to create recognition task");
        }

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
                transactionTemplate.executeWithoutResult(s -> updateTaskOcrId(task.getId(), celeryTaskId));
                log.info("Sent task to latexOCR, Celery task_id: {}, Local ID: {}", celeryTaskId, task.getId());
            } else {
                transactionTemplate.executeWithoutResult(s -> markTaskAsFailed(task.getId(), "LatexOCR HTTP Response " + response.getStatusCode().value()));
            }
        } catch (Exception e) {
            log.error("Failed to send image to LatexOCR", e);
            transactionTemplate.executeWithoutResult(s -> markTaskAsFailed(task.getId(), e.getMessage()));
        }
        return task;
    }


    public RecognitionTask initiateIndependentSolve(java.util.UUID userId, SolveRequestDto dto) {
        RecognitionTask task = transactionTemplate.execute(status -> createIndependentTask(userId, dto));
        sendToCasEngine(task);
        return task;
    }

    protected RecognitionTask saveInitialTask(String tempImagePath, java.util.UUID userId) {
        RecognitionTask task = RecognitionTask.builder()
                .userId(userId)
                .status(RecognitionStatus.RECOGNIZING)
                .imagePath(tempImagePath)
                .build();
        return taskRepository.save(task);
    }

    protected RecognitionTask createIndependentTask(java.util.UUID userId, SolveRequestDto dto) {
        RecognitionTask task = RecognitionTask.builder()
                .userId(userId)
                .status(RecognitionStatus.SOLVING_EQUATION)
                .originalResult(dto.getEquation())
                .imagePath(null)
                .build();
        return taskRepository.save(task);
    }




    @Transactional
    public void processOcrResult(OcrResultDto dto) {
        Optional<RecognitionTask> optionalTask = taskRepository.findByLatexOcrTaskId(dto.getTaskId());
        if (optionalTask.isEmpty()) {
            log.warn("Received OCR result for unknown celery task_id: {}. Requeuing...", dto.getTaskId());
            throw new RuntimeException("Task not found for OCR ID: " + dto.getTaskId());
        }

        RecognitionTask task = optionalTask.get();

        // Идемпотентность: обрабатываем только если задача еще в статусе распознавания
        if (task.getStatus() != RecognitionStatus.RECOGNIZING) {
            log.info("Received OCR result for task {} which is already in status {}", task.getId(), task.getStatus());
            return;
        }

        if ("SUCCESS".equals(dto.getStatus())) {
            task.setStatus(RecognitionStatus.READY_FOR_FEEDBACK);
            task.setOriginalResult(dto.getResult());
            task.setFeedbackDeadline(LocalDateTime.now().plusMinutes(30));
            taskRepository.save(task);
            rabbitTemplate.convertAndSend(RabbitMQConfig.FEEDBACK_DELAY_EXCHANGE, "", task.getId());
            log.info("Task {} is READY_FOR_FEEDBACK. Delayed 30 min check scheduled.", task.getId());
        } else {
            markTaskAsFailed(task.getId(), dto.getError());
        }
    }


    public RecognitionTask handleUserFeedback(String taskId, FeedbackRequestDto feedbackRequestDto) {
        RecognitionTask task = transactionTemplate.execute(status -> updateTaskWithFeedback(taskId, feedbackRequestDto));
        sendToCasEngine(task);
        return task;
    }

    protected RecognitionTask updateTaskWithFeedback(String taskId, FeedbackRequestDto feedbackRequestDto) {
        RecognitionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.getStatus() != RecognitionStatus.READY_FOR_FEEDBACK) {
            throw new RuntimeException("Task is not awaiting feedback");
        }

        if (Boolean.TRUE.equals(feedbackRequestDto.getEditStatus())) {
            task.setStatus(RecognitionStatus.COMPLETED_EDITED);
            task.setEditedResult(feedbackRequestDto.getEditedResult());
            if (task.getUserId() != null) {
                userStatisticsRepository.incrementEditedStats(task.getUserId());
            }
        } else {
            task.setStatus(RecognitionStatus.COMPLETED_AUTO);
            task.setEditedResult(null);
        }
        return taskRepository.save(task);
    }

    public void handleFeedbackTimeout(String taskId) {
        Optional<RecognitionTask> updatedTask = transactionTemplate.execute(status -> markTaskAsTimedOut(taskId));
        updatedTask.ifPresent(this::sendToCasEngine);
    }

    protected Optional<RecognitionTask> markTaskAsTimedOut(String taskId) {
        Optional<RecognitionTask> optionalTask = taskRepository.findById(taskId);
        if (optionalTask.isEmpty()) return Optional.empty();
        RecognitionTask task = optionalTask.get();
        if (task.getStatus() == RecognitionStatus.READY_FOR_FEEDBACK) {
            log.info("Feedback timeout reached for task {}. Auto-completing.", taskId);
            task.setStatus(RecognitionStatus.COMPLETED_AUTO);
            if (task.getImagePath() != null) {
                minioService.deleteFromTempBucket(task.getImagePath());
                task.setImagePath(null);
            }
            return Optional.of(taskRepository.save(task));
        }
        return Optional.empty();
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
                transactionTemplate.executeWithoutResult(s -> updateTaskCasInfo(task.getId(), casTaskId));
                log.info("Sent equation to CAS Engine, CAS task_id: {}, Local ID: {}", casTaskId, task.getId());
            } else {
                transactionTemplate.executeWithoutResult(s -> markTaskAsFailed(task.getId(), "CAS Engine HTTP Response " + response.getStatusCode().value()));
            }
        } catch (Exception e) {
            log.error("Failed to send equation to CAS engine", e);
            transactionTemplate.executeWithoutResult(s -> markTaskAsFailed(task.getId(), "CAS Engine failed: " + e.getMessage()));
        }
    }

    protected void updateTaskCasInfo(String id, String casTaskId) {
        taskRepository.findById(id).ifPresent(task -> {
            task.setCasEngineTaskId(casTaskId);
            task.setStatus(RecognitionStatus.SOLVING_EQUATION);
            taskRepository.save(task);
        });
    }

    protected void markTaskAsFailed(String taskId, String error) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == RecognitionStatus.FAILED) return; // Уже помечена как ошибка
            
            task.setStatus(RecognitionStatus.FAILED);
            task.setOriginalResult("Error: " + error);
            if (task.getImagePath() != null) {
                minioService.deleteFromTempBucket(task.getImagePath());
                task.setImagePath(null);
            }
            taskRepository.save(task);
            if (task.getUserId() != null) {
                int directInc = (task.getLatexOcrTaskId() == null) ? 1 : 0;
                userStatisticsRepository.upsertStats(task.getUserId(), 0, 1, directInc);
            }
            log.error("Task {} marked as failed. Reason: {}", task.getId(), error);
        });
    }

    protected void updateTaskOcrId(String taskId, String celeryTaskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setLatexOcrTaskId(celeryTaskId);
            taskRepository.save(task);
        });
    }

    @Transactional
    public void processCasResult(com.n4d3sh1k4.solution_archive_service.dto.CasResultDto dto) {
        taskRepository.findByCasEngineTaskId(dto.getTaskId())
                .ifPresentOrElse(task -> {
                    // Идемпотентность: обрабатываем результат только если задача еще в процессе
                    if (task.getStatus() != RecognitionStatus.SOLVING_EQUATION) {
                        log.info("Received result for task {} which is already in status {}", task.getId(), task.getStatus());
                        return;
                    }

                    boolean isSuccess = "SUCCESS".equals(dto.getStatus());
                    if (isSuccess) {
                        handleSuccess(task, dto);
                    } else {
                        markTaskAsFailed(task.getId(), dto.getError());
                    }
                }, () -> {
                    log.warn("Received CAS result for unknown task_id: {}. Requeuing...", dto.getTaskId());
                    throw new RuntimeException("Task not found for CAS ID: " + dto.getTaskId());
                });
    }

    private void handleSuccess(RecognitionTask task, com.n4d3sh1k4.solution_archive_service.dto.CasResultDto dto) {
        try {
            task.setSolutionResult(objectMapper.writeValueAsString(dto.getResult()));
            task.setStatus(RecognitionStatus.SOLUTION_READY);
            taskRepository.save(task);

            if (task.getImagePath() != null && task.getEditedResult() != null) {
                minioService.moveToDatasetBucket(task.getImagePath());
                DatasetEntry data = DatasetEntry.builder()
                        .originalTaskId(task.getId())
                        .latexContent(task.getEditedResult())
                        .imagePath(task.getImagePath())
                        .createdAt(LocalDateTime.now())
                        .build();
                datasetEntryRepository.save(data);
            }
            int directInc = (task.getLatexOcrTaskId() == null) ? 1 : 0;
            if (task.getUserId() != null) {
                userStatisticsRepository.upsertStats(task.getUserId(), 1, 0, directInc);
            }

            log.info("Solution received for task {}", task.getId());
        } catch (Exception e) {
            markTaskAsFailed(task.getId(), "Failed to serialize solution");
        }
    }
}