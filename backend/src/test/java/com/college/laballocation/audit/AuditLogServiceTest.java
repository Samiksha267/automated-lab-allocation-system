package com.college.laballocation.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.laballocation.audit.AuditLogDtos.AuditLogResponse;
import com.college.laballocation.audit.AuditLogDtos.AuditLogSearchCriteria;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * {@link AuditLogService} is the single write path (PART 10/19) and the
 * actor-resolution strategy (PART 21) - these are proven here without a real
 * database since neither depends on Postgres-specific behavior (the trigger
 * itself is proven in {@code AuditLogImmutabilityIT}).
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    private AuditLogService service;

    private static void setId(AppUser user, Long id) {
        try {
            Field field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void recordPersistsAnAuditLogBuiltExactlyFromTheEvent() {
        service = new AuditLogService(auditLogRepository, userRepository);
        AuditEvent event = new AuditEvent(
                7L, UserRole.CR, AuditAction.EXTRA_LAB_BOOKED, AuditResourceType.ALLOCATION, 501L,
                "Lab C-101 2026-08-24 09:00-11:00", 3L, 8L, Map.of("subjectId", 12L));

        service.record(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(7L);
        assertThat(saved.getActorRole()).isEqualTo(UserRole.CR);
        assertThat(saved.getAction()).isEqualTo(AuditAction.EXTRA_LAB_BOOKED);
        assertThat(saved.getResourceType()).isEqualTo(AuditResourceType.ALLOCATION);
        assertThat(saved.getResourceId()).isEqualTo(501L);
        assertThat(saved.getAcademicTermId()).isEqualTo(3L);
        assertThat(saved.getDivisionId()).isEqualTo(8L);
        assertThat(saved.getMetadata()).containsEntry("subjectId", 12L);
    }

    @Test
    void searchResolvesEachDistinctActorWithExactlyOneBulkLookup() {
        service = new AuditLogService(auditLogRepository, userRepository);
        AppUser actor = new AppUser("cr@example.edu", "hash", UserRole.CR, "CR One");
        setId(actor, 7L);
        AuditLog log1 = new AuditLog(7L, UserRole.CR, AuditAction.EXTRA_LAB_BOOKED, AuditResourceType.ALLOCATION, 501L, "r1", 3L, 8L, Map.of());
        AuditLog log2 = new AuditLog(7L, UserRole.CR, AuditAction.EXTRA_LAB_CANCELLED, AuditResourceType.ALLOCATION, 502L, "r2", 3L, 8L, Map.of());

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log1, log2)));
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(actor));

        var page = service.search(
                new AuditLogSearchCriteria(null, null, null, null, null, null, null), PageRequest.of(0, 20));

        verify(userRepository).findAllById(List.of(7L));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allSatisfy(r -> {
            assertThat(r.actorUserId()).isEqualTo(7L);
            assertThat(r.actorDisplayName()).isEqualTo("CR One");
        });
    }

    @Test
    void searchToleratesAnActorThatNoLongerResolves() {
        service = new AuditLogService(auditLogRepository, userRepository);
        AuditLog log = new AuditLog(99L, UserRole.CR, AuditAction.EXTRA_LAB_BOOKED, AuditResourceType.ALLOCATION, 501L, "r1", null, null, Map.of());
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAllById(List.of(99L))).thenReturn(List.of());

        var page = service.search(
                new AuditLogSearchCriteria(null, null, null, null, null, null, null), PageRequest.of(0, 20));

        AuditLogResponse response = page.getContent().get(0);
        assertThat(response.actorUserId()).isEqualTo(99L);
        assertThat(response.actorDisplayName()).isNull();
        assertThat(response.actorEmail()).isNull();
    }
}
