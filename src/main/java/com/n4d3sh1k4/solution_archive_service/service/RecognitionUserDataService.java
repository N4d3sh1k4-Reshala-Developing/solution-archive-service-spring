package com.n4d3sh1k4.solution_archive_service.service;

import com.n4d3sh1k4.common.exception.ContentNotFoundException;
import com.n4d3sh1k4.solution_archive_service.dto.RecognitionHistoryResponse;
import com.n4d3sh1k4.solution_archive_service.dto.UserStatisticsResponse;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import com.n4d3sh1k4.solution_archive_service.model.UserStatistics;
import com.n4d3sh1k4.solution_archive_service.repository.RecognitionTaskRepository;
import com.n4d3sh1k4.solution_archive_service.repository.UserStatisticsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecognitionUserDataService {
    private final RecognitionTaskRepository recognitionRepository;
    private final UserStatisticsRepository userStatisticsRepository;

    @Transactional
    public List<RecognitionHistoryResponse> getUserHistory(UUID userId) {
        log.info("User ID: {}", userId);
        return recognitionRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(RecognitionHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTask(String taskId, java.util.UUID userId) {
        RecognitionTask task = recognitionRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ContentNotFoundException("Task not found or access denied"));
        recognitionRepository.delete(task);
    }

    @Transactional
    public UserStatistics getUserStatistic(UUID userId){
        return userStatisticsRepository.findByUserId(userId)
                .orElseThrow(() -> new ContentNotFoundException("User not found or access denied"));
    }
}
