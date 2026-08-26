package com.college.laballocation.user;

import com.college.laballocation.user.UserDtos.UserSummaryResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 20 — minimal read-only account listing (see {@link UserService}'s javadoc for exact scope). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public List<UserSummaryResponse> list(@RequestParam UserRole role) {
        return userService.listByRole(role);
    }
}
