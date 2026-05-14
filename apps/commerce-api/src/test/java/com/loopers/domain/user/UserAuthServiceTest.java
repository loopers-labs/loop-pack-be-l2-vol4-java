package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAuthService userAuthService;

    @DisplayName("인증할 때,")
    @Nested
    class Authenticate {

        @DisplayName("로그인 ID 와 비밀번호가 일치하면, 해당 사용자를 반환한다.")
        @Test
        void returnsUser_whenLoginIdAndPasswordMatch() {
            // given
            LoginId loginId = LoginId.of("user01");
            String rawPassword = "Abcd1234!";
            String encodedPassword = "$2a$10$encodedHash";
            UserModel user = UserModel.create(
                loginId,
                Password.encoded(encodedPassword),
                "김철수",
                BirthDate.of(LocalDate.of(1999, 3, 22)),
                Email.of("user@example.com")
            );
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);

            // when
            UserModel result = userAuthService.authenticate(loginId, rawPassword);

            // then
            assertThat(result).isSameAs(user);
        }

        @DisplayName("해당 로그인 ID 사용자가 존재하지 않으면, UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenLoginIdDoesNotExist() {
            // given
            LoginId loginId = LoginId.of("nobody");
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                userAuthService.authenticate(loginId, "Abcd1234!")
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // given
            LoginId loginId = LoginId.of("user01");
            String rawPassword = "Wrong9999!";
            String encodedPassword = "$2a$10$encodedHash";
            UserModel user = UserModel.create(
                loginId,
                Password.encoded(encodedPassword),
                "김철수",
                BirthDate.of(LocalDate.of(1999, 3, 22)),
                Email.of("user@example.com")
            );
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(false);

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                userAuthService.authenticate(loginId, rawPassword)
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
