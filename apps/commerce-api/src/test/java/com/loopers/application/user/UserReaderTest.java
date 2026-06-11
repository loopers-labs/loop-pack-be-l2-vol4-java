package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserReaderTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserReader userReader = new UserReader(userRepository);

    private User existingUser() {
        return User.create(
            "loopers01", "encoded-pw", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
    }

    @Test
    @DisplayName("userId로 조회하면 해당 사용자를 반환한다")
    void get_returnsUser() {
        User user = existingUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userReader.get(1L);

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
    void get_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userReader.get(999L))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }
}
