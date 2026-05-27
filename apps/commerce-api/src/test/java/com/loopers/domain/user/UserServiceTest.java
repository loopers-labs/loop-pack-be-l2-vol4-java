package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserService userService;

    private UserRepository userRepository;
    private FakePasswordEncryptor passwordEncryptor;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncryptor = new FakePasswordEncryptor("encrypted:");
        userService = new UserService(userRepository, passwordEncryptor);
    }

    @DisplayName("회원 가입을 할 때, ")
    @Nested
    class SignUp {

        @DisplayName("가입된 적 없는 ID 이면 정상적으로 수행된다.")
        @Test
        void signUp() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;
            UserModel expected = new UserModel(loginId, loginPw, name, birthDate, email, gender, passwordEncryptor);

            when(userRepository.existsByLoginId(loginId)).thenReturn(false);
            when(userRepository.save(any())).thenReturn(expected);

            // when
            UserModel result = userService.create(loginId, loginPw, name, birthDate, email, gender);

            // then
            assertThat(result).isSameAs(expected);
        }

        @DisplayName("이미 가입된 ID 로 회원 가입 시도 시 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictException_whenDuplicateLoginIdIsProvided() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            when(userRepository.existsByLoginId(loginId)).thenReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.create(loginId, loginPw, name, birthDate, email, gender)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("로그인 유저를 조회할 때,")
    @Nested
    class GetLoginUser {

        @DisplayName("아이디 비밀번호가 모두 일치할 경우 회원 정보가 반환된다.")
        @Test
        void returnsLoginUser_whenLoginIdAndLoginPwIsValid() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            UserModel user = new UserModel(loginId, loginPw, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));

            // when
            UserModel result = userService.getLoginUser(loginId, loginPw);

            // then
            assertThat(result).isSameAs(user);
        }

        @DisplayName("아이디가 일치하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenUserDoesNotExist() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> userService.getLoginUser(loginId, loginPw));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginPwAuthenticationFails() {
            // given
            String loginId = "user01";
            UserModel user = new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));

            // when
            CoreException result = assertThrows(CoreException.class, () -> userService.getLoginUser(loginId, "WrongPass1!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
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

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            // when / then
            assertDoesNotThrow(() -> userService.changePassword(loginId, loginPw, oldPassword, newPassword));
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
                    userService.changePassword(loginId, loginPw, oldPassword, newPassword)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
