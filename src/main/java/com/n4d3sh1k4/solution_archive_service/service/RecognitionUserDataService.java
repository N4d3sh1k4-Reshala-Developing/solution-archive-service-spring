package com.n4d3sh1k4.solution_archive_service.service;

import com.n4d3sh1k4.solution_archive_service.dto.RecognitionHistoryResponse;
import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import com.n4d3sh1k4.solution_archive_service.repository.RecognitionTaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecognitionUserDataService {
    private final RecognitionTaskRepository recognitionRepository;

    @Transactional
    public List<RecognitionHistoryResponse> getUserHistory(UUID userId) {
        log.info("User ID: {}", userId);
        return recognitionRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(RecognitionHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
