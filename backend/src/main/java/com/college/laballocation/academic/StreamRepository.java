package com.college.laballocation.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamRepository extends JpaRepository<Stream, Long> {
    List<Stream> findByProgramId(Long programId);

    boolean existsByProgramIdAndCode(Long programId, String code);

    Optional<Stream> findByProgramIdAndCode(Long programId, String code);
}
