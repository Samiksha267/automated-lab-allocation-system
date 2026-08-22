package com.college.laballocation.lab;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.lab.LabUnavailabilityDtos.CreateLabUnavailabilityRequest;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Proves the half-open interval rule ({@code end > start}, PART 15 of the phase brief) is enforced before any lookup happens. */
@ExtendWith(MockitoExtension.class)
class LabUnavailabilityServiceTest {

    @Mock
    private LabUnavailabilityRepository unavailabilityRepository;

    @Mock
    private LabService labService;

    @Mock
    private UserRepository userRepository;

    private LabUnavailabilityService service;

    @Test
    void endBeforeStartIsRejected() {
        service = new LabUnavailabilityService(unavailabilityRepository, labService, userRepository);
        Instant start = Instant.now();
        Instant end = start.minus(1, ChronoUnit.HOURS);

        var request = new CreateLabUnavailabilityRequest(start, end, "Maintenance");

        assertThatThrownBy(() -> service.create(1L, request, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_UNAVAILABILITY_INTERVAL");
    }

    @Test
    void endEqualToStartIsRejected() {
        service = new LabUnavailabilityService(unavailabilityRepository, labService, userRepository);
        Instant start = Instant.now();

        var request = new CreateLabUnavailabilityRequest(start, start, "Maintenance");

        assertThatThrownBy(() -> service.create(1L, request, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_UNAVAILABILITY_INTERVAL");
    }

    @Test
    void validIntervalProceedsToLookup() {
        service = new LabUnavailabilityService(unavailabilityRepository, labService, userRepository);
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);
        when(labService.getEntity(1L)).thenThrow(new RuntimeException("expected - stops before user lookup"));

        var request = new CreateLabUnavailabilityRequest(start, end, "Maintenance");

        // A valid interval passes the interval check and proceeds to resolve the lab -
        // proven by reaching the (deliberately failing) labService stub rather than
        // failing earlier on the interval validation itself.
        assertThatThrownBy(() -> service.create(1L, request, 1L)).hasMessageContaining("expected");
    }
}
