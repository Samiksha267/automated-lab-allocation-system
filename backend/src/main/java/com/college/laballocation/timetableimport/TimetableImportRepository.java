package com.college.laballocation.timetableimport;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableImportRepository extends JpaRepository<TimetableImport, Long>, JpaSpecificationExecutor<TimetableImport> {

    /**
     * Row-locks this import for the remainder of the caller's transaction -
     * {@code TimetableImportService.approve} acquires this before
     * revalidating and creating allocations (PART 33/35), so two concurrent
     * approval attempts for the <em>same</em> import serialize. Concurrent
     * approvals of two <em>different, conflicting</em> imports are instead
     * protected by the underlying {@code Allocation} concurrency guarantees
     * (Phase 16 exclusion constraints, reused unchanged - PART 36).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TimetableImport i where i.id = :id")
    Optional<TimetableImport> lockById(@Param("id") Long id);
}
