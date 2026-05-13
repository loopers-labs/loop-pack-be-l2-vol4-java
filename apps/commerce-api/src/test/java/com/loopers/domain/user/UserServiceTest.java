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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private static final LoginId LOGIN_ID = new LoginId("kim99");
    private static final Name NAME = new Name("홍길동");
    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(1999, 1, 1));
    private static final Email EMAIL = new Email("kim@loopers.com");
    private static final String RAW_PASSWORD = "Abcd123!";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedPasswordHashValue";

    private UserCommand.SignUp signUpCommand() {
        return new UserCommand.SignUp(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, RAW_PASSWORD);
    }

    @DisplayName("회원 가입 시, ")
    @Nested
    class SignUp {

        @DisplayName("loginId 가 중복되지 않으면 비밀번호를 인코딩해 영속화한 뒤 UserModel 을 반환한다.")
        @Test
        void savesUserWithEncodedPassword_whenLoginIdIsUnique() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            UserModel result = userService.signUp(signUpCommand());

            // assert
            assertThat(result)
                .extracting(
                    UserModel::getLoginId,
                    UserModel::getName,
                    UserModel::getBirthDate,
                    UserModel::getEmail,
                    UserModel::getEncodedPassword
                )
                .containsExactly(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, ENCODED_PASSWORD);
            verify(passwordEncoder).encode(RAW_PASSWORD);
            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("loginId 가 이미 존재하면 CONFLICT 예외가 발생하고 인코딩/저장은 일어나지 않는다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.signUp(signUpCommand()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(UserModel.class));
        }

        @DisplayName("비밀번호가 정책에 위반되면 BAD_REQUEST 예외가 발생하고 인코딩/저장은 일어나지 않는다.")
        @Test
        void throwsBadRequest_whenPasswordViolatesPolicy() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(false);
            UserCommand.SignUp invalid = new UserCommand.SignUp(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, "short");

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.signUp(invalid));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(UserModel.class));
        }
    }
}
