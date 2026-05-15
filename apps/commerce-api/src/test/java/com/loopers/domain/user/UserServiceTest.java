package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserModel user;

    @BeforeEach
    void setUp() {
        user = new UserModel("user123", "encodedPassword!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
    }

    @DisplayName("register()를 호출할 때,")
    @Nested
    class Register {

        @DisplayName("Given 중복되지 않은 loginId / When 등록 요청 / Then 저장된 UserModel이 반환된다.")
        @Test
        void returnsRegisteredUser_whenLoginIdIsUnique() {
            // arrange
            given(userRepository.existsByLoginId(user.getLoginId())).willReturn(false);
            given(userRepository.save(user)).willReturn(user);

            // act
            UserModel result = userService.register(user);

            // assert
            assertThat(result).isEqualTo(user);
        }

        @DisplayName("Given 이미 존재하는 loginId / When 등록 요청 / Then CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdIsDuplicated() {
            // arrange
            given(userRepository.existsByLoginId(user.getLoginId())).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.register(user)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            then(userRepository).should(never()).save(user);
        }
    }

    @DisplayName("changePassword()를 호출할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("Given 새 인코딩된 비밀번호 / When 변경 요청 / Then 비밀번호가 갱신되고 저장된다.")
        @Test
        void updatesPasswordAndSaves_whenCalled() {
            // arrange
            given(userRepository.save(user)).willReturn(user);

            // act
            userService.changePassword(user, "newEncodedPassword!");

            // assert
            assertThat(user.getPassword()).isEqualTo("newEncodedPassword!");
            then(userRepository).should().save(user);
        }
    }

    @DisplayName("authenticate()를 호출할 때,")
    @Nested
    class Authenticate {

        @DisplayName("Given 올바른 loginId와 비밀번호 / When 인증 요청 / Then UserModel이 반환된다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            given(userRepository.findByLoginId(user.getLoginId())).willReturn(Optional.of(user));
            given(passwordEncoder.matches("rawPassword", user.getPassword())).willReturn(true);

            // act
            UserModel result = userService.authenticate(user.getLoginId(), "rawPassword");

            // assert
            assertThat(result).isEqualTo(user);
        }

        @DisplayName("Given 존재하지 않는 loginId / When 인증 요청 / Then UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdNotFound() {
            // arrange
            given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.authenticate("unknown", "rawPassword")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("Given 틀린 비밀번호 / When 인증 요청 / Then UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            given(userRepository.findByLoginId(user.getLoginId())).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPassword", user.getPassword())).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.authenticate(user.getLoginId(), "wrongPassword")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
