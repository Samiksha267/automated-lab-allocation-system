package com.college.laballocation.academic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.CrAssignmentDtos.CreateCrAssignmentRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Proves "a CRAssignment target user must actually have role CR" (PART 16 of the phase brief). */
@ExtendWith(MockitoExtension.class)
class CrAssignmentServiceTest {

    @Mock
    private CrAssignmentRepository crAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DivisionService divisionService;

    @Mock
    private AcademicTermService academicTermService;

    private CrAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new CrAssignmentService(crAssignmentRepository, userRepository, divisionService, academicTermService);
    }

    @Test
    void assigningANonCrUserIsRejected() {
        AppUser student = new AppUser("student@example.edu", "hash", UserRole.STUDENT, "A Student");
        when(userRepository.findById(5L)).thenReturn(Optional.of(student));

        var request = new CreateCrAssignmentRequest(5L, 1L, 1L);

        assertThatThrownBy(() -> service.create(request, 99L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }
}
