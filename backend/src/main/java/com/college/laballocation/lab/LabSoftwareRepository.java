package com.college.laballocation.lab;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabSoftwareRepository extends JpaRepository<LabSoftware, Long> {
    // JOIN FETCH: CandidateAllocationFactory reads getSoftware().getCode() on every
    // result outside of this query's own transaction (it builds a snapshot with no
    // surrounding @Transactional of its own) - a plain derived query would hand back
    // an uninitializable lazy Software proxy once that transaction closes.
    @Query("SELECT ls FROM LabSoftware ls JOIN FETCH ls.software WHERE ls.lab.id = :labId")
    List<LabSoftware> findByLabId(@Param("labId") Long labId);

    Optional<LabSoftware> findByLabIdAndSoftwareId(Long labId, Long softwareId);

    boolean existsByLabIdAndSoftwareId(Long labId, Long softwareId);
}
