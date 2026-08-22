package com.college.laballocation.lab;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** {@link JpaSpecificationExecutor} backs the static capability filtering in {@link LabSpecifications}. */
public interface LabRepository extends JpaRepository<Lab, Long>, JpaSpecificationExecutor<Lab> {
    boolean existsByCode(String code);

    Optional<Lab> findByCode(String code);
}
