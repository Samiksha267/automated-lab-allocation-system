package com.college.laballocation.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Only the queries actually needed by authentication - no speculative generic methods. */
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
