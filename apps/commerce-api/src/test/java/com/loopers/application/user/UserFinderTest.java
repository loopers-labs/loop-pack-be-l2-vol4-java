package com.loopers.application.user;

import com.loopers.domain.user.FakePasswordEncryptor;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserFinderTest {

    private UserFinder userFinder;

    private UserRepository userRepository;

    private PasswordEncryptor passwordEncryptor;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncryptor = new FakePasswordEncryptor("encrypted:");

        userFinder = new UserFinder(userRepository, passwordEncryptor);
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
            UserModel result = userFinder.getLoginUser(loginId, loginPw);

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
            CoreException result = assertThrows(CoreException.class, () -> userFinder.getLoginUser(loginId, loginPw));

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
            CoreException result = assertThrows(CoreException.class, () -> userFinder.getLoginUser(loginId, "WrongPass1!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
