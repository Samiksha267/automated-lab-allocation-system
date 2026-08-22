package com.college.laballocation.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ForbiddenDivisionAccessException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Proves CR ownership resolution never trusts anything but the authenticated
 * user's own {@link CrAssignment} row (docs/09-AUTHORIZATION-RBAC.md, HC-11) -
 * required by PART 19/49 of the phase brief, at the service level, before any
 * scheduling endpoint exists to exercise it end-to-end.
 */
@ExtendWith(MockitoExtension.class)
class CrOwnershipServiceTest {

    @Mock
    private CrAssignmentRepository crAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    private CrOwnershipService service;

    private static final Long CR_USER_ID = 10L;
    private static final Long DIVISION_A_ID = 100L;
    private static final Long DIVISION_B_ID = 200L;

    @BeforeEach
    void setUp() {
        service = new CrOwnershipService(crAssignmentRepository, userRepository);
    }

    private AppUser userWithRole(UserRole role) {
        return new AppUser("user@example.edu", "hash", role, "Test User");
    }

    private Division divisionWithId(Long id) {
        Division division = new Division(mockYear(), "A", 60);
        setId(division, id);
        return division;
    }

    private AcademicYear mockYear() {
        Stream stream = new Stream(new Program("BTECH", "B.Tech", 4), "CS", "Computer Science");
        return new AcademicYear(stream, 3);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void crAssignedToDivisionAOwnsDivisionA() {
        AppUser cr = userWithRole(UserRole.CR);
        when(userRepository.findById(CR_USER_ID)).thenReturn(Optional.of(cr));
        AcademicTerm activeTerm = activeTerm();
        CrAssignment assignment = new CrAssignment(cr, divisionWithId(DIVISION_A_ID), activeTerm, cr);
        when(crAssignmentRepository.findByUserIdOrderByCreatedAtDesc(CR_USER_ID)).thenReturn(List.of(assignment));

        CrAssignment result = service.requireOwnsDivision(CR_USER_ID, DIVISION_A_ID);

        assertThat(result.getDivision().getId()).isEqualTo(DIVISION_A_ID);
    }

    @Test
    void sameCrCannotClaimDivisionBOnceAssignedToDivisionA() {
        AppUser cr = userWithRole(UserRole.CR);
        when(userRepository.findById(CR_USER_ID)).thenReturn(Optional.of(cr));
        CrAssignment assignment = new CrAssignment(cr, divisionWithId(DIVISION_A_ID), activeTerm(), cr);
        when(crAssignmentRepository.findByUserIdOrderByCreatedAtDesc(CR_USER_ID)).thenReturn(List.of(assignment));

        assertThatThrownBy(() -> service.requireOwnsDivision(CR_USER_ID, DIVISION_B_ID))
                .isInstanceOf(ForbiddenDivisionAccessException.class);
    }

    @Test
    void studentAttemptingCrOwnershipPathIsForbidden() {
        AppUser student = userWithRole(UserRole.STUDENT);
        when(userRepository.findById(CR_USER_ID)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.requireOwnsDivision(CR_USER_ID, DIVISION_A_ID))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void crWithNoActiveAssignmentGetsAClearNotFoundError() {
        AppUser cr = userWithRole(UserRole.CR);
        when(userRepository.findById(CR_USER_ID)).thenReturn(Optional.of(cr));
        when(crAssignmentRepository.findByUserIdOrderByCreatedAtDesc(CR_USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireOwnsDivision(CR_USER_ID, DIVISION_A_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "CR_ASSIGNMENT_NOT_FOUND");
    }

    private AcademicTerm activeTerm() {
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5", java.time.LocalDate.now(), java.time.LocalDate.now().plusMonths(4));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }
}
