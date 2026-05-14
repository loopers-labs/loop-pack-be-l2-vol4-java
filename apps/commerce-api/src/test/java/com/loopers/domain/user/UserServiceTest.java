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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_LOGIN_ID = "loopers01";
    private static final String VALID_RAW_PASSWORD = "Aa3!xyz@";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);
    private static final String VALID_EMAIL = "test@loopers.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncryptor passwordEncryptor;

    @InjectMocks
    private UserService userService;

    private UserModel validUser() {
        return new UserModel(
            new LoginId(VALID_LOGIN_ID),
            ENCODED_PASSWORD,
            new UserName(VALID_NAME),
            VALID_BIRTH_DATE,
            new Email(VALID_EMAIL)
        );
    }

    @DisplayName("회원가입 시")
    @Nested
    class Signup {

        @DisplayName("중복되지 않은 로그인 ID와 유효한 입력이면 PasswordEncryptor.encode 결과를 저장한다")
        @Test
        void savesUserWithEncodedPassword_whenInputIsValid() {
            // given
            when(userRepository.existsByLoginId(VALID_LOGIN_ID)).thenReturn(false);
            when(passwordEncryptor.encode(VALID_RAW_PASSWORD, VALID_BIRTH_DATE)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(UserModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            UserModel saved = userService.signup(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // then
            assertThat(saved.getLoginId().getValue()).isEqualTo(VALID_LOGIN_ID);
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
                userService.signup(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(userRepository, never()).save(any(UserModel.class));
        }

        @DisplayName("PasswordEncryptor가 BAD_REQUEST를 던지면 그대로 전파되고 저장되지 않는다")
        @Test
        void throwsBadRequest_whenPasswordEncryptorRejects() {
            // given
            when(userRepository.existsByLoginId(VALID_LOGIN_ID)).thenReturn(false);
            when(passwordEncryptor.encode(anyString(), any())).thenThrow(new CoreException(ErrorType.BAD_REQUEST, "정책 위반"));

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.signup(VALID_LOGIN_ID, "Aa!2002xy", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
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
            UserModel user = validUser();
            when(userRepository.findByLoginId(VALID_LOGIN_ID)).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(VALID_RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

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
            UserModel user = validUser();
            when(userRepository.findByLoginId(VALID_LOGIN_ID)).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(anyString(), anyString())).thenReturn(false);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.authenticate(VALID_LOGIN_ID, "Wrong7$z@")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("ID로 조회 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID면 유저를 반환한다")
        @Test
        void returnsUser_whenIdExists() {
            // given
            Long userId = 1L;
            UserModel user = validUser();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserModel result = userService.getById(userId);

            // then
            assertThat(result).isSameAs(user);
        }

        @DisplayName("존재하지 않는 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> userService.getById(userId));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("비밀번호 변경 시")
    @Nested
    class ChangePassword {

        @DisplayName("현재 일치, 새 비밀번호가 현재와 다르고 정책 통과면 새 인코딩 결과로 변경된다")
        @Test
        void changesPassword_whenAllConditionsAreMet() {
            // given
            Long userId = 1L;
            UserModel user = validUser();
            String currentPassword = "Current1!";
            String newPassword = "NewPass1@";
            String newEncoded = "$2a$10$newEncodedHash";
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(currentPassword, ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncryptor.matches(newPassword, ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncryptor.encode(newPassword, VALID_BIRTH_DATE)).thenReturn(newEncoded);

            // when
            userService.changePassword(userId, currentPassword, newPassword);

            // then
            assertThat(user.getEncodedPassword()).isEqualTo(newEncoded);
        }

        @DisplayName("존재하지 않는 유저 ID로 변경 시도하면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(userId, "Current1!", "NewPass1@")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            // given
            Long userId = 1L;
            UserModel user = validUser();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(anyString(), anyString())).thenReturn(false);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(userId, "Wrong1!p", "NewPass1@")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 BAD_REQUEST 예외가 발생하고 encode가 호출되지 않는다")
        @Test
        void throwsBadRequest_whenNewPasswordEqualsCurrentPassword() {
            // given
            Long userId = 1L;
            UserModel user = validUser();
            String samePassword = "SamePass1!";
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(samePassword, ENCODED_PASSWORD)).thenReturn(true);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(userId, samePassword, samePassword)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(passwordEncryptor, never()).encode(anyString(), any());
        }

        @DisplayName("PasswordEncryptor.encode가 BAD_REQUEST를 던지면 그대로 전파된다")
        @Test
        void throwsBadRequest_whenPasswordEncryptorRejectsNewPassword() {
            // given
            Long userId = 1L;
            UserModel user = validUser();
            String currentPassword = "Current1!";
            String newPasswordWithBirthYear = "Aa!2002xy";
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(currentPassword, ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncryptor.matches(newPasswordWithBirthYear, ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncryptor.encode(newPasswordWithBirthYear, VALID_BIRTH_DATE))
                .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "정책 위반"));

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.changePassword(userId, currentPassword, newPasswordWithBirthYear)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
