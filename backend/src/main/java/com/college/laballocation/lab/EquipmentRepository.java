package com.college.laballocation.lab;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByCode(String code);

    boolean existsByCode(String code);
}
