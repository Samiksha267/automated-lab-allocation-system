package com.college.laballocation.academic;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DivisionRepository extends JpaRepository<Division, Long> {
    List<Division> findByAcademicYearId(Long academicYearId);

    boolean existsByAcademicYearIdAndCode(Long academicYearId, String code);

    Optional<Division> findByAcademicYearIdAndCode(Long academicYearId, String code);

    /**
     * Acquires a {@code SELECT ... FOR UPDATE} row lock on this division for
     * the remainder of the caller's transaction - Phase 16's per-division
     * concurrency guard (see {@code ExtraLabService.book}, ADR-073,
     * docs/15-DESIGN-DECISIONS.md). Every EXTRA booking for a division
     * acquires this same lock before revalidating HC-05 (DIVISION-vs-BATCH
     * cross-type conflict), which cannot be expressed as a single symmetric
     * PostgreSQL exclusion constraint - serializing, not rejecting,
     * concurrent bookings within one division. The returned entity itself is
     * not used for its data; the lock is the point.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Division d where d.id = :id")
    Optional<Division> lockById(@Param("id") Long id);
}
