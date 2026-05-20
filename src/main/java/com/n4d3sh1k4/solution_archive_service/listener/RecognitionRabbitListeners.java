package com.n4d3sh1k4.solution_archive_service.listener;

import com.n4d3sh1k4.solution_archive_service.config.RabbitMQConfig;
import com.n4d3sh1k4.solution_archive_service.dto.OcrResultDto;
import com.n4d3sh1k4.solution_archive_service.service.RecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecognitionRabbitListeners {

    private final RecognitionService recognitionService;

    @RabbitListener(queues = RabbitMQConfig.OCR_RESULTS_QUEUE)
    public void handleOcrResult(OcrResultDto resultDto) {
        log.info("Received OCR Result from RabbitMQ for Celery Task: {}", resultDto.getTaskId());
        try {
            recognitionService.processOcrResult(resultDto);
        } catch (Exception e) {
            // Если это не ошибка гонки (задача не найдена), то просто логируем и не переповторяем, 
            // чтобы не забивать очередь бесконечными ретраями уже решенных задач
            if (e.getMessage() != null && e.getMessage().contains("Task not found")) {
                throw e; // Позволяем requeue только если задача еще не долетела до БД
            }
            log.error("Error processing OCR result (ignoring to prevent loop): {}", e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.FEEDBACK_PROCESS_QUEUE)
    public void handleFeedbackTimeout(String taskId) {
        log.info("Received feedback timeout message from DLQ for Task: {}", taskId);
        recognitionService.handleFeedbackTimeout(taskId);
    }

    @RabbitListener(queues = RabbitMQConfig.CAS_RESULTS_QUEUE)
    public void handleCasResult(com.n4d3sh1k4.solution_archive_service.dto.CasResultDto resultDto) {
        log.info("Received CAS Result from RabbitMQ for CAS Task: {}", resultDto.getTaskId());
        recognitionService.processCasResult(resultDto);
    }
}
