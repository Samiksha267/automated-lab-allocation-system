package com.college.laballocation.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    List<AcademicYear> findByStreamId(Long streamId);

    boolean existsByStreamIdAndYearNumber(Long streamId, int yearNumber);

    Optional<AcademicYear> findByStreamIdAndYearNumber(Long streamId, int yearNumber);
}
