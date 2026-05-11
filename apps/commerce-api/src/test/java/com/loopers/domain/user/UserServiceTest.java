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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static final String VALID_LOGIN_ID = "loopers01";
    private static final String VALID_RAW_PASSWORD = "Aa3!xyz@";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);
    private static final String VALID_EMAIL = "test@loopers.com";

    @DisplayName("회원가입 시")
    @Nested
    class SignUp {

        @DisplayName("중복되지 않은 로그인 ID와 유효한 입력이면 비밀번호를 암호화해 저장한다")
        @Test
        void savesUserWithEncodedPassword_whenInputIsValid() {
            // given
            when(userRepository.existsByLoginId(VALID_LOGIN_ID)).thenReturn(false);
            when(passwordEncoder.encode(VALID_RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(UserModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            UserModel saved = userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // then
            assertThat(saved.getLoginId()).isEqualTo(VALID_LOGIN_ID);
            assertThat(saved.getEncodedPassword()).isEqualTo(ENCODED_PASSWORD);
            verify(userRepository, times(1)).save(any(UserModel.class));
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // given
            when(userRepository.existsByLoginId(VALID_LOGIN_ID)).thenReturn(true);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(userRepository, never()).save(any(UserModel.class));
        }

        @DisplayName("비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // given
            when(userRepository.existsByLoginId(VALID_LOGIN_ID)).thenReturn(false);
            String passwordWithBirthYear = "Aa!2002xy";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.signUp(VALID_LOGIN_ID, passwordWithBirthYear, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).save(any(UserModel.class));
        }
    }

    @DisplayName("인증 시")
    @Nested
    class Authenticate {

        @DisplayName("로그인 ID와 비밀번호가 일치하면 유저 정보를 반환한다")
        @Test
        void returnsUser_whenCredentialsMatch() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            when(userRepository.findByLoginId(VALID_LOGIN_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(VALID_RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            // when
            UserModel result = userService.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD);

            // then
            assertThat(result).isSameAs(user);
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorized_whenLoginIdDoesNotExist() {
            // given
            when(userRepository.findByLoginId(VALID_LOGIN_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            when(userRepository.findByLoginId(VALID_LOGIN_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.authenticate(VALID_LOGIN_ID, "wrongPassword!")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호 변경 시")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호와 새 비밀번호가 모두 유효하면 새 암호화된 비밀번호로 변경된다")
        @Test
        void changesPassword_whenCurrentAndNewPasswordsAreValid() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            String currentPassword = "Current1!";
            String newPassword = "NewPass1@";
            String newEncoded = "$2a$10$newEncodedHash";
            when(passwordEncoder.matches(currentPassword, ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.matches(newPassword, ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncoder.encode(newPassword)).thenReturn(newEncoded);

            // when
            userService.changePassword(user, currentPassword, newPassword);

            // then
            assertThat(user.getEncodedPassword()).isEqualTo(newEncoded);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(user, "wrong!Pw1", "NewPass1@")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNewPasswordEqualsCurrentPassword() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            String samePassword = "SamePass1!";
            when(passwordEncoder.matches(samePassword, ENCODED_PASSWORD)).thenReturn(true);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(user, samePassword, samePassword)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            String currentPassword = "Current1!";
            String newPasswordWithBirthYear = "Aa!2002xy";
            when(passwordEncoder.matches(currentPassword, ENCODED_PASSWORD)).thenReturn(true);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(user, currentPassword, newPasswordWithBirthYear)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
