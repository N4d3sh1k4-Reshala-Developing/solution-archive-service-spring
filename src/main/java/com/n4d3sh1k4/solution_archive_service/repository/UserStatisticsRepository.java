package com.n4d3sh1k4.solution_archive_service.repository;

import com.n4d3sh1k4.solution_archive_service.model.UserStatistics;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserStatisticsRepository extends JpaRepository<UserStatistics, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO user_statistics (user_id, total_tasks, success_tasks, error_tasks, direct_solution_tasks, edited_tasks)
            VALUES (:userId, 1, :success, :error, :direct, 0)
            ON CONFLICT (user_id) DO UPDATE SET
                total_tasks = user_statistics.total_tasks + 1,
                success_tasks = user_statistics.success_tasks + :success,
                error_tasks = user_statistics.error_tasks + :error,
                direct_solution_tasks = user_statistics.direct_solution_tasks + :direct
            """, nativeQuery = true)
    void upsertStats(@Param("userId") UUID userId,
                     @Param("success") int success,
                     @Param("error") int error,
                     @Param("direct") int direct);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO user_statistics (user_id, edited_tasks, total_tasks, success_tasks, error_tasks, direct_solution_tasks)
            VALUES (:userId, 1, 0, 0, 0, 0)
            ON CONFLICT (user_id) DO UPDATE SET
                edited_tasks = user_statistics.edited_tasks + 1
            """, nativeQuery = true)
    void incrementEditedStats(@Param("userId") UUID userId);

    Optional<UserStatistics> findByUserId(UUID userId);
}
