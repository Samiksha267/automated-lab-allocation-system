package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRole;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Pure domain/entity object - no Spring context, no database. */
class AllocationTest {

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static long nextDivisionId = 100L;
    private static long nextBatchId = 200L;

    private Division division(String code, int strength) {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Division division = new Division(year, code, strength);
        setId(division, nextDivisionId++);
        return division;
    }

    private Batch batch(Division division, String code, int strength) {
        Batch batch = new Batch(division, code, strength);
        setId(batch, nextBatchId++);
        return batch;
    }

    private Subject subject() {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Subject subject = new Subject(year, "BDA", "Big Data Analytics");
        setId(subject, 300L);
        return subject;
    }

    private Faculty faculty() {
        Faculty faculty = new Faculty("FAC-BDA", "Faculty BDA", null, null);
        setId(faculty, 400L);
        return faculty;
    }

    private Lab lab() {
        LabType labType = new LabType("COMPUTER", "Computer Lab", null);
        setId(labType, 500L);
        Lab lab = new Lab("C-301", "Computer Lab 301", 70, labType, "C", "3", "301");
        setId(lab, 501L);
        return lab;
    }

    private ScheduleVersion scheduleVersion() {
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5 (2026-27)", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 12, 15));
        term.updateStatus(TermStatus.ACTIVE);
        setId(term, 600L);
        ScheduleVersion version = new ScheduleVersion(term, 1, null, appUser());
        setId(version, 601L);
        return version;
    }

    private AppUser appUser() {
        AppUser user = new AppUser("lab.assistant@example.edu", "hash", UserRole.LAB_ASSISTANT, "Lab Assistant");
        setId(user, 700L);
        return user;
    }

    @Test
    void batchAllocationForBatchBelongingToDivisionSucceeds() {
        Division division = division("A", 68);
        Batch batch = batch(division, "A1", 23);

        Allocation allocation = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject(), faculty(), lab(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, scheduleVersion(), appUser());

        assertThat(allocation.getTargetType()).isEqualTo(TargetType.BATCH);
        assertThat(allocation.getBatch()).isEqualTo(batch);
    }

    @Test
    void batchAllocationRequiresANonNullBatch() {
        Division division = division("A", 68);

        assertThatThrownBy(() -> Allocation.forBatch(
                        AllocationType.EXTRA, division, null, subject(), faculty(), lab(),
                        LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                        AllocationStatus.APPROVED, scheduleVersion(), appUser()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void batchAllocationRejectsABatchFromADifferentDivision() {
        Division divisionA = division("A", 68);
        Division divisionB = division("B", 60);
        Batch batchFromA = batch(divisionA, "A1", 23);

        assertThatThrownBy(() -> Allocation.forBatch(
                        AllocationType.EXTRA, divisionB, batchFromA, subject(), faculty(), lab(),
                        LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                        AllocationStatus.APPROVED, scheduleVersion(), appUser()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ACADEMIC_RELATIONSHIP");
    }

    @Test
    void divisionAllocationHasNoBatch() {
        Division division = division("A", 68);

        Allocation allocation = Allocation.forDivision(
                AllocationType.REGULAR, division, subject(), faculty(), lab(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, scheduleVersion(), appUser());

        assertThat(allocation.getTargetType()).isEqualTo(TargetType.DIVISION);
        assertThat(allocation.getBatch()).isNull();
    }

    @Test
    void invalidTimeRangeIsRejectedRegardlessOfTargetType() {
        Division division = division("A", 68);

        assertThatThrownBy(() -> Allocation.forDivision(
                        AllocationType.REGULAR, division, subject(), faculty(), lab(),
                        LocalDate.of(2026, 8, 24), LocalTime.of(11, 0), LocalTime.of(9, 0),
                        AllocationStatus.APPROVED, scheduleVersion(), appUser()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ALLOCATION_INTERVAL");
    }

    @Test
    void publishTransitionsApprovedToPublished() {
        Allocation allocation = Allocation.forDivision(
                AllocationType.REGULAR, division("A", 68), subject(), faculty(), lab(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, scheduleVersion(), appUser());

        allocation.publish();

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.PUBLISHED);
    }

    @Test
    void publishingAnAlreadyPublishedAllocationIsRejected() {
        Allocation allocation = Allocation.forDivision(
                AllocationType.REGULAR, division("A", 68), subject(), faculty(), lab(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, scheduleVersion(), appUser());

        assertThatThrownBy(allocation::publish)
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ALLOCATION_TRANSITION");
    }

    @Test
    void cancellingAnAlreadyCancelledAllocationIsRejected() {
        Allocation allocation = Allocation.forDivision(
                AllocationType.REGULAR, division("A", 68), subject(), faculty(), lab(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.CANCELLED, scheduleVersion(), appUser());

        assertThatThrownBy(() -> allocation.cancel(appUser(), "duplicate booking"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ALLOCATION_TRANSITION");
    }

    @Test
    void cancellingAnApprovedAllocationSucceeds() {
        Allocation allocation = Allocation.forDivision(
                AllocationType.REGULAR, division("A", 68), subject(), faculty(), lab(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, scheduleVersion(), appUser());

        allocation.cancel(appUser(), "cancelled for maintenance");

        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.CANCELLED);
        assertThat(allocation.getCancellationReason()).isEqualTo("cancelled for maintenance");
    }
}
