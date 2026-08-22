package com.college.laballocation.faculty;

import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultyAvailabilityRepository extends JpaRepository<FacultyAvailability, Long> {

    List<FacultyAvailability> findByFacultyIdOrderByDayOfWeekAscStartTimeAsc(Long facultyId);

    List<FacultyAvailability> findByFacultyIdAndAcademicTermIdOrderByDayOfWeekAscStartTimeAsc(
            Long facultyId, Long academicTermId);

    List<FacultyAvailability> findByFacultyIdAndDayOfWeekOrderByStartTimeAsc(Long facultyId, DayOfWeek dayOfWeek);

    List<FacultyAvailability> findByFacultyIdAndAcademicTermIdAndDayOfWeekOrderByStartTimeAsc(
            Long facultyId, Long academicTermId, DayOfWeek dayOfWeek);

    /** Active-only, used for overlap validation and availability evaluation - never loads inactive/history rows. */
    List<FacultyAvailability> findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
            Long facultyId, Long academicTermId, DayOfWeek dayOfWeek);
}
