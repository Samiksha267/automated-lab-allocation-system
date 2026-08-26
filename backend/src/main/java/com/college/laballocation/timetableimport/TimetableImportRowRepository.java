package com.college.laballocation.timetableimport;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableImportRowRepository extends JpaRepository<TimetableImportRow, Long> {

    List<TimetableImportRow> findByTimetableImportIdOrderByRowNumberAsc(Long timetableImportId);

    Page<TimetableImportRow> findByTimetableImportIdOrderByRowNumberAsc(Long timetableImportId, Pageable pageable);

    Optional<TimetableImportRow> findByIdAndTimetableImportId(Long id, Long timetableImportId);

    long countByTimetableImportIdAndValidationStatus(Long timetableImportId, ImportRowStatus status);
}
