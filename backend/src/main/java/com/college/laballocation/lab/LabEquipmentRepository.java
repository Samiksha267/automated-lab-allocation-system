package com.college.laballocation.lab;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabEquipmentRepository extends JpaRepository<LabEquipment, Long> {
    List<LabEquipment> findByLabId(Long labId);

    Optional<LabEquipment> findByLabIdAndEquipmentId(Long labId, Long equipmentId);

    boolean existsByLabIdAndEquipmentId(Long labId, Long equipmentId);
}
