package com.n4d3sh1k4.solution_archive_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recognition_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecognitionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "latex_ocr_task_id")
    private String latexOcrTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecognitionStatus status;

    @Column(name = "original_result", columnDefinition = "TEXT")
    private String originalResult;

    @Column(name = "edited_result", columnDefinition = "TEXT")
    private String editedResult;

    @Column(name = "image_path")
    private String imagePath; // path in MinIO

    @Column(name = "feedback_deadline")
    private LocalDateTime feedbackDeadline;
    
    @Column(name = "user_id")
    private java.util.UUID userId;

    @Column(name = "cas_engine_task_id")
    private String casEngineTaskId;

    @Column(name = "solution_result", columnDefinition = "TEXT")
    private String solutionResult;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
