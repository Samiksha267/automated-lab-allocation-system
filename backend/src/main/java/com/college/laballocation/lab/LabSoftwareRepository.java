package com.college.laballocation.lab;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabSoftwareRepository extends JpaRepository<LabSoftware, Long> {
    List<LabSoftware> findByLabId(Long labId);

    Optional<LabSoftware> findByLabIdAndSoftwareId(Long labId, Long softwareId);

    boolean existsByLabIdAndSoftwareId(Long labId, Long softwareId);
}
