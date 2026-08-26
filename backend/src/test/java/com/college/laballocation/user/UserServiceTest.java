package com.college.laballocation.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void listByRoleMapsToSummaryResponses() {
        UserService service = new UserService(userRepository);
        AppUser cr = new AppUser("cr@example.edu", "hash", UserRole.CR, "Demo CR");
        when(userRepository.findByRole(UserRole.CR)).thenReturn(List.of(cr));

        List<UserDtos.UserSummaryResponse> result = service.listByRole(UserRole.CR);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("cr@example.edu");
        assertThat(result.get(0).role()).isEqualTo(UserRole.CR);
        assertThat(result.get(0).active()).isTrue();
    }
}
