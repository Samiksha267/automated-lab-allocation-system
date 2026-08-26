package com.college.laballocation.lab;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabEquipmentRepository extends JpaRepository<LabEquipment, Long> {
    // JOIN FETCH: CandidateAllocationFactory reads getEquipment().getCode() on every
    // result outside of this query's own transaction - see LabSoftwareRepository's
    // identical findByLabId for the full reasoning.
    @Query("SELECT le FROM LabEquipment le JOIN FETCH le.equipment WHERE le.lab.id = :labId")
    List<LabEquipment> findByLabId(@Param("labId") Long labId);

    Optional<LabEquipment> findByLabIdAndEquipmentId(Long labId, Long equipmentId);

    boolean existsByLabIdAndEquipmentId(Long labId, Long equipmentId);
}
