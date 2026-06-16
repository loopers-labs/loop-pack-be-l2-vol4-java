package com.loopers.user.application;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.user.domain.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserQueryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserQueryService userQueryService = new UserQueryService(userRepository);

    @Test
    @DisplayName("getUser 는 userId 로 사용자를 조회해 Detail 로 반환한다")
    void givenExistingUserId_whenGetUser_thenReturnsDetail() {
        User user = User.create(
            "loopers01", "encoded-pw", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResult.Detail result = userQueryService.getUser(1L);

        assertThat(result.loginId()).isEqualTo("loopers01");
    }

    @Test
    @DisplayName("getUser 는 사용자가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
    void givenNonExistingUserId_whenGetUser_thenThrowsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.getUser(999L))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }
}
