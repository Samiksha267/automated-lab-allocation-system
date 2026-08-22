package com.college.laballocation.scheduling;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleVersionRepository extends JpaRepository<ScheduleVersion, Long> {

    List<ScheduleVersion> findByAcademicTermIdOrderByVersionNumberAsc(Long academicTermId);

    Optional<ScheduleVersion> findByAcademicTermIdAndStatus(Long academicTermId, ScheduleVersionStatus status);

    int countByAcademicTermId(Long academicTermId);
}
