package com.college.laballocation.subject;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByAcademicYearId(Long academicYearId);

    boolean existsByAcademicYearIdAndCode(Long academicYearId, String code);

    Optional<Subject> findByAcademicYearIdAndCode(Long academicYearId, String code);
}
