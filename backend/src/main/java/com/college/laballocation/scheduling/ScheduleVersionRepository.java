package com.college.laballocation.scheduling;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleVersionRepository extends JpaRepository<ScheduleVersion, Long> {

    List<ScheduleVersion> findByAcademicTermIdOrderByVersionNumberAsc(Long academicTermId);

    Optional<ScheduleVersion> findByAcademicTermIdAndStatus(Long academicTermId, ScheduleVersionStatus status);

    int countByAcademicTermId(Long academicTermId);

    /**
     * A scalar-only projection, deliberately not {@code findById} (Phase 18):
     * used purely to discover which term to lock ({@code AcademicTermRepository.lockById})
     * <b>before</b> the target {@link ScheduleVersion} entity is loaded into
     * the persistence context - loading the full entity first and locking
     * second would let a concurrent transaction's already-committed status
     * change go unnoticed, since a second {@code findById} on an
     * already-managed entity in the same session returns the stale
     * first-level-cached instance rather than re-querying. See
     * {@code ScheduleVersionService.publish}.
     */
    @Query("select sv.academicTerm.id from ScheduleVersion sv where sv.id = :id")
    Optional<Long> findAcademicTermIdById(@Param("id") Long id);
}
