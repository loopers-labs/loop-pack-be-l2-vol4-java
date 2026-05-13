package com.loopers.domain.user;

import com.loopers.domain.user.service.UserSignupService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSignupServiceTest {

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
    private UserSignupService userSignupService;

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
            UserModel saved = userSignupService.signup(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

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
                userSignupService.signup(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
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
                userSignupService.signup(VALID_LOGIN_ID, "Aa!2002xy", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).save(any(UserModel.class));
        }
    }
}
