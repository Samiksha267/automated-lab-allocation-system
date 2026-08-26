package com.college.laballocation.lab;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** {@link JpaSpecificationExecutor} backs the static capability filtering in {@link LabSpecifications}. */
public interface LabRepository extends JpaRepository<Lab, Long>, JpaSpecificationExecutor<Lab> {
    boolean existsByCode(String code);

    Optional<Lab> findByCode(String code);

    /** Phase 23 analytics - the universe of labs utilization/unused-lab reporting considers; a retired lab has nothing to report on. */
    List<Lab> findByActiveTrue();

    List<Lab> findByActiveTrueAndWing(String wing);
}
