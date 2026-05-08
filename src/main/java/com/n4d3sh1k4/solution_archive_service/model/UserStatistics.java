package com.n4d3sh1k4.solution_archive_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_statistics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatistics {
    @Id
    private UUID userId;

    @Builder.Default
    private long totalTasks = 0;

    @Builder.Default
    private long successTasks = 0;

    @Builder.Default
    private long errorTasks = 0;

    @Builder.Default
    private long editedTasks = 0;

    @Builder.Default
    private long directSolutionTasks = 0;
}