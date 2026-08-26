package com.college.laballocation.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Phase 20 — the Lab Assistant CR-management UI needs to pick an existing CR account to assign; no account-creation endpoint exists (out of this project's scope, see docs/15-DESIGN-DECISIONS.md). */
    List<AppUser> findByRole(UserRole role);
}
