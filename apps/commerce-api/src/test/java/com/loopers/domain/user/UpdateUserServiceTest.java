package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserServiceTest {

    private UpdateUserService updateUserService;

    private FindUserService findUserService;

    private PasswordEncryptor passwordEncryptor;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        findUserService = mock(FindUserService.class);
        passwordEncryptor = new FakePasswordEncryptor("encrypted:");
        userRepository = mock(UserRepository.class);

        updateUserService = new UpdateUserService(findUserService, passwordEncryptor, userRepository);
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("정상적으로 변경된다.")
        @Test
        void changePassword() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String oldPassword = "Password1!";
            String newPassword = "NewPass99!";
            UserModel user = new UserModel(loginId, loginPw, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);

            when(findUserService.getLoginUser(loginId, loginPw)).thenReturn(user);

            // when
            updateUserService.changePassword(loginId, loginPw, oldPassword, newPassword);

            // then
            verify(userRepository).save(user);
        }

        @DisplayName("loginPw 와 oldPassword 가 일치하지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginPwAndOldPasswordDoNotMatch() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String oldPassword = "WrongPass1!";
            String newPassword = "NewPass99!";

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    updateUserService.changePassword(loginId, loginPw, oldPassword, newPassword)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(findUserService, never()).getLoginUser(loginId, loginPw);
            verify(userRepository, never()).save(any());
        }
    }
}
