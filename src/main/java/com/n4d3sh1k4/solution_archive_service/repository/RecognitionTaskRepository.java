package com.n4d3sh1k4.solution_archive_service.repository;

import com.n4d3sh1k4.solution_archive_service.model.RecognitionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecognitionTaskRepository extends JpaRepository<RecognitionTask, String> {
    Optional<RecognitionTask> findByLatexOcrTaskId(String latexOcrTaskId);
    List<RecognitionTask> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
