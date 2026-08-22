package com.college.laballocation.lab;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareRepository extends JpaRepository<Software, Long> {
    Optional<Software> findByCode(String code);

    boolean existsByCode(String code);
}
