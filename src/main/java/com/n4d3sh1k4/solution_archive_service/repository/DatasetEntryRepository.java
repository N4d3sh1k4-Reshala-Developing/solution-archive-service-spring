package com.n4d3sh1k4.solution_archive_service.repository;

import com.n4d3sh1k4.solution_archive_service.model.DatasetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetEntryRepository extends JpaRepository<DatasetEntry, String> {
}
