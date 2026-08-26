package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PART 14/50 (mandatory) - proves two genuinely simultaneous publish requests
 * for two different DRAFT versions of the same term can never both end up
 * {@code PUBLISHED} at once. This project's chosen strategy (the per-term
 * {@code AcademicTermRepository.lockById} pessimistic lock, ADR-087,
 * docs/15-DESIGN-DECISIONS.md) <b>serializes</b> rather than rejects: unlike
 * Phase 16's booking path (where a PostgreSQL exclusion constraint rejects
 * the loser outright), both publish calls here succeed - whichever
 * transaction acquires the lock second simply re-reads the now-current
 * PUBLISHED version (the first call's committed result) and correctly
 * supersedes it before publishing its own target. The phase brief
 * explicitly sanctions either outcome ("CONFLICT / serialized result...
 * actual result may differ") - the invariant that actually matters, and
 * that this test asserts, is that at most one version is ever
 * simultaneously {@code PUBLISHED} for one term.
 *
 * <p>True concurrency via an {@code ExecutorService}/{@code CountDownLatch}
 * barrier, mirroring {@code AllocationConcurrencyIT}'s Phase 16 pattern
 * exactly (ADR-073) - deliberately <b>not</b> {@code @Transactional} at the
 * test-method level, which would make both "concurrent" calls share one
 * outer test transaction and never actually race. Environment-blocked on
 * this development machine (same documented Docker/Testcontainers-on-Windows
 * limitation as every other IT class, docs/13-DEVELOPER-SETUP.md);
 * independently proven live against the real Dockerized stack
 * (docs/11-TESTING-STRATEGY.md).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ScheduleVersionConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ScheduleVersionRepository scheduleVersionRepository;

    @Autowired
    private ScheduleVersionService scheduleVersionService;

    /** PART 50 - V1 already PUBLISHED, V2/V3 both DRAFT; publish(V2) and publish(V3) race - exactly one must win. */
    @Test
    void concurrentPublishOfTwoDraftsForTheSameTermProducesExactlyOnePublishedVersion() throws Exception {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("CONCVER-YR", 1, "Concurrency Version Test Term", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        AppUser labAssistant = userRepository.save(
                new AppUser("concver-la@example.edu", passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));

        ScheduleVersion v1 = scheduleVersionService.createDraft(term.getId(), null, labAssistant.getId());
        scheduleVersionService.publish(v1.getId(), labAssistant.getId());
        ScheduleVersion v2 = scheduleVersionService.createDraft(term.getId(), "revision 1", labAssistant.getId());
        ScheduleVersion v3 = scheduleVersionService.createDraft(term.getId(), "revision 2", labAssistant.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Future<?> f1 = executor.submit(() -> attemptPublish(v2.getId(), labAssistant.getId(), ready, go, successes, conflicts));
        Future<?> f2 = executor.submit(() -> attemptPublish(v3.getId(), labAssistant.getId(), ready, go, successes, conflicts));

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // The per-term lock serializes rather than rejects (see class javadoc) - both publish calls succeed,
        // one after the other, the second correctly superseding the first's already-committed result.
        assertThat(successes.get()).isEqualTo(2);
        assertThat(conflicts.get()).isEqualTo(0);

        List<ScheduleVersion> allVersions = scheduleVersionRepository.findByAcademicTermIdOrderByVersionNumberAsc(term.getId());
        long publishedCount = allVersions.stream().filter(v -> v.getStatus() == ScheduleVersionStatus.PUBLISHED).count();
        assertThat(publishedCount).isEqualTo(1);
        // Every version - the original v1, the winner, and the loser - must still exist (historical preservation, PART 24).
        assertThat(allVersions).hasSize(3);
        // v1 was already superseded before the race began; exactly one of v2/v3 must now also be superseded (the loser of the race), the other PUBLISHED.
        long supersededCount = allVersions.stream().filter(v -> v.getStatus() == ScheduleVersionStatus.SUPERSEDED).count();
        assertThat(supersededCount).isEqualTo(2);
    }

    private void attemptPublish(
            Long versionId, Long userId, CountDownLatch ready, CountDownLatch go, AtomicInteger successes, AtomicInteger conflicts) {
        try {
            ready.countDown();
            go.await();
            scheduleVersionService.publish(versionId, userId);
            successes.incrementAndGet();
        } catch (ApiException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
