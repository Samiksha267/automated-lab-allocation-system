package com.college.laballocation.academic;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    List<AcademicTerm> findByStatus(TermStatus status);

    Optional<AcademicTerm> findByAcademicYearLabelAndTermNumber(String academicYearLabel, int termNumber);

    /**
     * Acquires a {@code SELECT ... FOR UPDATE} row lock on this term for the
     * remainder of the caller's transaction - Phase 18's per-term concurrency
     * guard, mirroring {@code DivisionRepository.lockById}/ADR-073 exactly.
     * {@code ScheduleVersionService.createDraft}/{@code publish} both acquire
     * this lock before computing the next version number or deciding which
     * version is currently {@code PUBLISHED}, so two concurrent requests for
     * the same term serialize rather than racing each other's read-then-write
     * decision. The returned entity itself is not used for its data; the
     * lock is the point.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AcademicTerm t where t.id = :id")
    Optional<AcademicTerm> lockById(@Param("id") Long id);
}
