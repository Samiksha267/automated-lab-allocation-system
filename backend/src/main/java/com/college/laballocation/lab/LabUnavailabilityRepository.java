package com.college.laballocation.lab;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabUnavailabilityRepository extends JpaRepository<LabUnavailability, Long> {
    List<LabUnavailability> findByLabIdOrderByStartDateTime(Long labId);
}
