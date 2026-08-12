package com.example.travlediary.controller.user;

import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserApiControllerNicknameTest {

    private final UserService userService = mock(UserService.class);
    private final UserApiController controller = new UserApiController(userService);

    @Test
    void existingSignupBooleanContractRemainsAvailableForValidNames() {
        when(userService.isNicknameExists("여행왕123")).thenReturn(false);

        Map<String, Object> response = controller.checkNickname("여행왕123");

        assertThat(response)
                .containsEntry("exists", false)
                .containsEntry("status", "AVAILABLE");
        verify(userService).isNicknameExists("여행왕123");
    }

    @Test
    void forbiddenSignupNicknameIsNeverReportedAsAvailable() {
        when(userService.isNicknameExists("관12리34자"))
                .thenThrow(new NicknamePolicy.ViolationException(
                        NicknamePolicy.ViolationType.FORBIDDEN,
                        NicknamePolicy.FORBIDDEN_MESSAGE));

        Map<String, Object> response = controller.checkNickname("관12리34자");

        assertThat(response)
                .containsEntry("exists", true)
                .containsEntry("status", "FORBIDDEN")
                .doesNotContainKey("word")
                .doesNotContainKey("category");
    }
}
