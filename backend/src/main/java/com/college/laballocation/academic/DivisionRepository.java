package com.college.laballocation.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DivisionRepository extends JpaRepository<Division, Long> {
    List<Division> findByAcademicYearId(Long academicYearId);

    boolean existsByAcademicYearIdAndCode(Long academicYearId, String code);

    Optional<Division> findByAcademicYearIdAndCode(Long academicYearId, String code);
}
