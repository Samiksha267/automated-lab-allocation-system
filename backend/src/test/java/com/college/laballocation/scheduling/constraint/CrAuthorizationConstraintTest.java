package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.DIVISION_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.CrAssignment;
import com.college.laballocation.academic.CrOwnershipService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRole;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Uses {@link CrOwnershipService#getCurrentAssignment} (non-throwing) rather
 * than {@code requireOwnsDivision} - see {@link CrAuthorizationConstraint}'s
 * javadoc for why: the throwing variant is itself {@code @Transactional},
 * and an exception crossing that boundary poisons the surrounding
 * transaction even when caught here, a real bug found via manual Docker
 * verification (see the Phase 9 completion report).
 */
@ExtendWith(MockitoExtension.class)
class CrAuthorizationConstraintTest {

    @Mock
    private CrOwnershipService crOwnershipService;

    private CrAuthorizationConstraint constraint;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Division division(Long id) {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Division division = new Division(year, "A", 68);
        setId(division, id);
        return division;
    }

    private CrAssignment assignmentFor(Long divisionId) {
        AppUser user = new AppUser("cr@example.edu", "hash", UserRole.CR, "CR User");
        AppUser labAssistant = new AppUser("lab.assistant@example.edu", "hash", UserRole.LAB_ASSISTANT, "LA");
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 1));
        term.updateStatus(TermStatus.ACTIVE);
        return new CrAssignment(user, division(divisionId), term, labAssistant);
    }

    @Test
    void idIsHc11() {
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_11_CR_AUTHORIZATION);
    }

    @Test
    void noActorIsNotApplicable() {
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        SchedulingContext context = SchedulingFixtures.batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0), null));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.NOT_APPLICABLE);
        verifyNoInteractions(crOwnershipService);
    }

    @Test
    void labAssistantActorIsNotApplicable() {
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        SchedulingActor actor = new SchedulingActor(1L, UserRole.LAB_ASSISTANT);
        SchedulingContext context = SchedulingFixtures.batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0), actor));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.NOT_APPLICABLE);
        verifyNoInteractions(crOwnershipService);
    }

    @Test
    void crOwningTheDivisionPasses() {
        SchedulingActor actor = new SchedulingActor(42L, UserRole.CR);
        when(crOwnershipService.getCurrentAssignment(42L)).thenReturn(Optional.of(assignmentFor(DIVISION_ID)));
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        SchedulingContext context = SchedulingFixtures.batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0), actor));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void crOwningADifferentDivisionFails() {
        SchedulingActor actor = new SchedulingActor(42L, UserRole.CR);
        when(crOwnershipService.getCurrentAssignment(42L)).thenReturn(Optional.of(assignmentFor(999L)));
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        SchedulingContext context = SchedulingFixtures.batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0), actor));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("FORBIDDEN_DIVISION_ACCESS");
    }

    @Test
    void crWithNoActiveAssignmentFails() {
        SchedulingActor actor = new SchedulingActor(42L, UserRole.CR);
        when(crOwnershipService.getCurrentAssignment(42L)).thenReturn(Optional.empty());
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        SchedulingContext context = SchedulingFixtures.batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0), actor));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("CR_ASSIGNMENT_NOT_FOUND");
    }

    @Test
    void studentActorDefensivelyFails() {
        SchedulingActor actor = new SchedulingActor(7L, UserRole.STUDENT);
        constraint = new CrAuthorizationConstraint(crOwnershipService);
        SchedulingContext context = SchedulingFixtures.batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0), actor));

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        verifyNoInteractions(crOwnershipService);
    }
}
