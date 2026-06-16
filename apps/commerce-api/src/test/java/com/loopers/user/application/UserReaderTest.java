package com.loopers.user.application;

import com.loopers.user.domain.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.user.domain.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserReaderTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserReader userReader = new UserReader(userRepository);

    @Test
    @DisplayName("ensureExists 는 존재하는 userId 면 예외 없이 통과한다")
    void givenExistingUserId_whenEnsureExists_thenDoesNotThrow() {
        when(userRepository.existsById(1L)).thenReturn(true);

        assertThatCode(() -> userReader.ensureExists(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ensureExists 는 존재하지 않는 userId 면 NOT_FOUND 예외가 발생한다")
    void givenNonExistingUserId_whenEnsureExists_thenThrowsNotFound() {
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> userReader.ensureExists(999L))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }
}
