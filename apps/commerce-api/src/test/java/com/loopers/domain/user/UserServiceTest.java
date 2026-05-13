package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_LOGIN_ID = "chanhee";
    private static final String VALID_RAW_PASSWORD = "chan1234!";
    private static final EncodedPassword ENCODED_PASSWORD = new EncodedPassword("Y2hhbjEyMzQhPGZha2UtaGFzaD4=");
    private static final String VALID_NAME = "김찬희";
    private static final String VALID_BIRTH_DATE = "1995-05-10";
    private static final String VALID_EMAIL = "chan950510@gmail.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입 처리 시")
    @Nested
    class SignUp {

        @DisplayName("중복되지 않은 아이디로 요청하면 암호화 후 가입완료")
        @Test
        void savesUserOnce_whenRequestIsValid() {
            // arrange
            given(userRepository.existsByLoginId(VALID_LOGIN_ID)).willReturn(false);
            given(passwordEncoder.encode(any(RawPassword.class))).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            ArgumentCaptor<UserModel> savedCaptor = ArgumentCaptor.forClass(UserModel.class);
            verify(userRepository, times(1)).existsByLoginId(VALID_LOGIN_ID);
            verify(passwordEncoder, times(1)).encode(new RawPassword(VALID_RAW_PASSWORD));
            verify(userRepository, times(1)).save(savedCaptor.capture());

            UserModel saved = savedCaptor.getValue();
            assertAll(
                    () -> assertThat(saved.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                    () -> assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD),
                    () -> assertThat(saved.getName()).isEqualTo(VALID_NAME),
                    () -> assertThat(saved.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                    () -> assertThat(saved.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("이미 존재하는 로그인ID면 CONFLICT 예외 발생")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            given(userRepository.existsByLoginId(VALID_LOGIN_ID)).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> verify(userRepository, times(1)).existsByLoginId(VALID_LOGIN_ID),
                    () -> verify(passwordEncoder, never()).encode(any(RawPassword.class)),
                    () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }

        @DisplayName("raw 비밀번호에 생년월일이 포함되어 있으면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenRawPasswordContainsBirthDate() {
            // arrange
            String passwordContainingBirthDate = "Pass19950510!";
            given(userRepository.existsByLoginId(VALID_LOGIN_ID)).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.signUp(VALID_LOGIN_ID, passwordContainingBirthDate, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> verify(passwordEncoder, never()).encode(any(RawPassword.class)),
                    () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }
    }

    @DisplayName("비밀번호 수정 처리 시")
    @Nested
    class ChangePassword {

        private static final String NEW_RAW_PASSWORD = "newPw5678!";
        private static final EncodedPassword CURRENT_ENCODED = new EncodedPassword("CurrentHashedValue==");
        private static final EncodedPassword NEW_ENCODED = new EncodedPassword("NewHashedValue==");

        @DisplayName("유효한 요청이면 새 비밀번호를 인코딩 후 저장한다.")
        @Test
        void changesPassword_whenRequestIsValid() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, CURRENT_ENCODED, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(VALID_RAW_PASSWORD, CURRENT_ENCODED)).willReturn(true);
            given(passwordEncoder.matches(NEW_RAW_PASSWORD, CURRENT_ENCODED)).willReturn(false);
            given(passwordEncoder.encode(any(RawPassword.class))).willReturn(NEW_ENCODED);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            userService.changePassword(VALID_LOGIN_ID, VALID_RAW_PASSWORD, NEW_RAW_PASSWORD);

            // assert
            ArgumentCaptor<UserModel> savedCaptor = ArgumentCaptor.forClass(UserModel.class);
            verify(userRepository, times(1)).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getPassword()).isEqualTo(NEW_ENCODED);
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 UNAUTHORIZED 예외 발생")
        @Test
        void throwsUnauthorized_whenOldPasswordDoesNotMatch() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, CURRENT_ENCODED, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(VALID_RAW_PASSWORD, CURRENT_ENCODED)).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(VALID_LOGIN_ID, VALID_RAW_PASSWORD, NEW_RAW_PASSWORD));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
                    () -> verify(passwordEncoder, never()).encode(any(RawPassword.class)),
                    () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenNewPasswordEqualsCurrent() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, CURRENT_ENCODED, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(VALID_RAW_PASSWORD, CURRENT_ENCODED)).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_RAW_PASSWORD));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> verify(passwordEncoder, never()).encode(any(RawPassword.class)),
                    () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            String passwordWithBirthDate = "Pass19950510!";
            UserModel user = new UserModel(VALID_LOGIN_ID, CURRENT_ENCODED, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(VALID_RAW_PASSWORD, CURRENT_ENCODED)).willReturn(true);
            given(passwordEncoder.matches(passwordWithBirthDate, CURRENT_ENCODED)).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(VALID_LOGIN_ID, VALID_RAW_PASSWORD, passwordWithBirthDate));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> verify(passwordEncoder, never()).encode(any(RawPassword.class)),
                    () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }
    }
}
