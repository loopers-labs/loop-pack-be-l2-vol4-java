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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("authenticate 는, ")
    @Nested
    class Authenticate {

        @DisplayName("로그인 ID 와 비밀번호가 일치하면, 해당 사용자를 반환한다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            LoginId loginId = new LoginId("minwoo01");
            String password = "Passw0rd!";
            String encodedPassword = "ENCODED";
            User user = new User(
                loginId,
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("minwoo@example.com"),
                encodedPassword
            );
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);

            // act
            User result = userService.authenticate(loginId, password);

            // assert
            assertThat(result).isSameAs(user);
        }

        @DisplayName("존재하지 않는 로그인 ID 면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenUserNotFound() {
            // arrange
            LoginId loginId = new LoginId("notexist01");
            String password = "Passw0rd!";
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> userService.authenticate(loginId, password)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            LoginId loginId = new LoginId("minwoo01");
            String password = "WrongPass1!";
            String encodedPassword = "ENCODED";
            User user = new User(
                loginId,
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("minwoo@example.com"),
                encodedPassword
            );
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(password, encodedPassword)).willReturn(false);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> userService.authenticate(loginId, password)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
