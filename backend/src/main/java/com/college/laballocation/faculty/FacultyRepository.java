package com.college.laballocation.faculty;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    boolean existsByEmployeeCode(String employeeCode);

    Optional<Faculty> findByEmployeeCode(String employeeCode);
}
