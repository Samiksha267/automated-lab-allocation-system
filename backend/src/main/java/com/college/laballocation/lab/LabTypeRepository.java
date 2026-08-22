package com.college.laballocation.lab;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTypeRepository extends JpaRepository<LabType, Long> {
    Optional<LabType> findByCode(String code);

    boolean existsByCode(String code);
}
