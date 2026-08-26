package com.college.laballocation.user;

import com.college.laballocation.user.UserDtos.UserSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 20 — read-only listing so the Lab Assistant CR-management UI can
 * pick an existing CR account to assign. No create/update/delete endpoint
 * exists here — account creation/registration is out of this project's
 * scope (docs/15-DESIGN-DECISIONS.md); accounts are seeded (`DevUserSeeder`)
 * or created by a future admin workflow, not by this controller.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSummaryResponse> listByRole(UserRole role) {
        return userRepository.findByRole(role).stream().map(UserSummaryResponse::from).toList();
    }
}
