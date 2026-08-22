package com.college.laballocation.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByDivisionId(Long divisionId);

    boolean existsByDivisionIdAndCode(Long divisionId, String code);

    Optional<Batch> findByDivisionIdAndCode(Long divisionId, String code);
}
