package com.loopers.domain.user;

import com.loopers.domain.user.service.UserAuthService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {

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
    private UserAuthService userAuthService;

    private UserModel validUser() {
        return new UserModel(
            new LoginId(VALID_LOGIN_ID),
            ENCODED_PASSWORD,
            new UserName(VALID_NAME),
            VALID_BIRTH_DATE,
            new Email(VALID_EMAIL)
        );
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
            UserModel result = userAuthService.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD);

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
                userAuthService.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD)
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
                userAuthService.authenticate(VALID_LOGIN_ID, "Wrong7$z@")
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
            UserModel result = userAuthService.getById(userId);

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
            CoreException ex = assertThrows(CoreException.class, () -> userAuthService.getById(userId));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
