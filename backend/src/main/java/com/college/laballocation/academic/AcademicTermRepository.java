package com.college.laballocation.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    List<AcademicTerm> findByStatus(TermStatus status);

    Optional<AcademicTerm> findByAcademicYearLabelAndTermNumber(String academicYearLabel, int termNumber);
}
