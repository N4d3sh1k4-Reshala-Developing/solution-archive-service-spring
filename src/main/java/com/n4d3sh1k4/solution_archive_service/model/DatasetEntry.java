package com.n4d3sh1k4.solution_archive_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dataset_entries")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class DatasetEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "original_task_id")
    private String originalTaskId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String latexContent;

    @Column(nullable = false)
    private String imagePath;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
