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
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicy passwordPolicy;

    private UserModel user;

    @BeforeEach
    void setUp() {
        user = new UserModel("user123", "Password1!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
    }

    @DisplayName("register()를 호출할 때,")
    @Nested
    class Register {

        @DisplayName("중복되지 않은 loginId 등록 시 비밀번호가 인코딩된 채 저장된 UserModel이 반환된다.")
        @Test
        void returnsRegisteredUser_whenLoginIdIsUnique() {
            // arrange
            String rawPassword = user.getPassword(); // 서비스 실행 전 원본 비밀번호 캡처 (서비스가 user 객체를 mutate하므로)
            willDoNothing().given(passwordPolicy).validate(new PasswordValidationContext(rawPassword, user.getBirthDate()));
            given(userRepository.existsByLoginId(user.getLoginId())).willReturn(false);
            given(passwordEncoder.encode(rawPassword)).willReturn("encodedHash!");
            given(userRepository.save(user)).willReturn(user);

            // act
            UserModel result = userService.register(user);

            // assert
            assertThat(result).isEqualTo(user);
            then(passwordPolicy).should().validate(new PasswordValidationContext(rawPassword, user.getBirthDate()));
            then(passwordEncoder).should().encode(rawPassword);
        }

        @DisplayName("이미 존재하는 loginId 등록 시 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdIsDuplicated() {
            // arrange
            willDoNothing().given(passwordPolicy).validate(new PasswordValidationContext(user.getPassword(), user.getBirthDate()));
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

        private static final String CURRENT_RAW = "Password1!";
        private static final String NEW_RAW = "NewPassword2@";

        @DisplayName("올바른 현재 비밀번호와 유효한 새 비밀번호로 변경 시 인코딩된 비밀번호로 갱신되고 저장된다.")
        @Test
        void updatesPasswordAndSaves_whenCurrentPasswordIsCorrectAndNewPasswordIsValid() {
            // arrange
            given(passwordEncoder.matches(CURRENT_RAW, user.getPassword())).willReturn(true);
            willDoNothing().given(passwordPolicy).validate(new PasswordValidationContext(NEW_RAW, user.getBirthDate()));
            given(passwordEncoder.matches(NEW_RAW, user.getPassword())).willReturn(false);
            given(passwordEncoder.encode(NEW_RAW)).willReturn("encodedNew!");
            given(userRepository.save(user)).willReturn(user);

            // act
            userService.changePassword(user, CURRENT_RAW, NEW_RAW);

            // assert
            assertThat(user.getPassword()).isEqualTo("encodedNew!");
            then(userRepository).should().save(user);
        }

        @DisplayName("틀린 현재 비밀번호로 변경 시 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenCurrentPasswordIsWrong() {
            // arrange
            given(passwordEncoder.matches("WrongPw1!", user.getPassword())).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(user, "WrongPw1!", NEW_RAW)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            then(userRepository).should(never()).save(user);
        }

        @DisplayName("현재와 동일한 새 비밀번호로 변경 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            given(passwordEncoder.matches(CURRENT_RAW, user.getPassword())).willReturn(true);
            willDoNothing().given(passwordPolicy).validate(new PasswordValidationContext(CURRENT_RAW, user.getBirthDate()));
            given(passwordEncoder.matches(CURRENT_RAW, user.getPassword())).willReturn(true); // same check

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(user, CURRENT_RAW, CURRENT_RAW)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(userRepository).should(never()).save(user);
        }
    }

    @DisplayName("authenticate()를 호출할 때,")
    @Nested
    class Authenticate {

        @DisplayName("올바른 loginId와 비밀번호로 인증 시 UserModel이 반환된다.")
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

        @DisplayName("존재하지 않는 loginId로 인증 시 UNAUTHORIZED 예외가 발생한다.")
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

        @DisplayName("틀린 비밀번호로 인증 시 UNAUTHORIZED 예외가 발생한다.")
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
