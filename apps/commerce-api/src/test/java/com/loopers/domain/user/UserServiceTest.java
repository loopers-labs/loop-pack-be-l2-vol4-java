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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입 시,")
    @Nested
    class SignUp {

        @DisplayName("유효한 입력이 주어지면, 회원을 저장하고 반환한다.")
        @Test
        void savesAndReturnsUser_whenValidInputIsProvided() {
            // given
            LoginId loginId = LoginId.of("user01");
            Password password = Password.of("Abcd1234!");
            String name = "김철수";
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));
            Email email = Email.of("user@example.com");

            given(userRepository.existsByLoginId(loginId)).willReturn(false);
            given(passwordEncoder.encode(anyString())).willAnswer(inv -> inv.getArgument(0));
            given(userRepository.save(any(UserModel.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            UserModel result = userService.signUp(loginId, password, name, birthDate, email);

            // then
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo(loginId),
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(result.getEmail()).isEqualTo(email)
            );
            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("유효한 입력이 주어지면, 비밀번호를 암호화하여 저장한다.")
        @Test
        void encodesPassword_whenValidInputIsProvided() {
            // given
            LoginId loginId = LoginId.of("user01");
            Password password = Password.of("Abcd1234!");
            String name = "김철수";
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));
            Email email = Email.of("user@example.com");
            String encodedValue = "$2a$10$encodedHashValue";

            given(userRepository.existsByLoginId(loginId)).willReturn(false);
            given(passwordEncoder.encode("Abcd1234!")).willReturn(encodedValue);
            given(userRepository.save(any(UserModel.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            userService.signUp(loginId, password, name, birthDate, email);

            // then
            ArgumentCaptor<UserModel> captor = ArgumentCaptor.forClass(UserModel.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword().getValue()).isEqualTo(encodedValue);
        }

        @DisplayName("이미 사용 중인 로그인 ID 면, CONFLICT 예외를 던진다.")
        @Test
        void throwsConflict_whenLoginIdIsDuplicated() {
            // given
            LoginId duplicatedLoginId = LoginId.of("user01");
            given(userRepository.existsByLoginId(duplicatedLoginId)).willReturn(true);

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(
                    duplicatedLoginId,
                    Password.of("Abcd1234!"),
                    "김철수",
                    BirthDate.of(LocalDate.of(1999, 3, 22)),
                    Email.of("user@example.com")
                )
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("이미 사용 중인 로그인 ID 면, 저장을 시도하지 않는다.")
        @Test
        void doesNotInvokeSave_whenLoginIdIsDuplicated() {
            // given
            LoginId duplicatedLoginId = LoginId.of("user01");
            given(userRepository.existsByLoginId(duplicatedLoginId)).willReturn(true);

            // when
            assertThrows(CoreException.class, () ->
                userService.signUp(
                    duplicatedLoginId,
                    Password.of("Abcd1234!"),
                    "김철수",
                    BirthDate.of(LocalDate.of(1999, 3, 22)),
                    Email.of("user@example.com")
                )
            );

            // then
            verify(userRepository, never()).save(any(UserModel.class));
        }

        @DisplayName("비밀번호에 생년월일이 yyyyMMdd 형식으로 포함되면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // given
            LoginId loginId = LoginId.of("user01");
            Password password = Password.of("ab19990322!");
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(
                    loginId,
                    password,
                    "김철수",
                    birthDate,
                    Email.of("user@example.com")
                )
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 저장을 시도하지 않는다.")
        @Test
        void doesNotInvokeSave_whenPasswordContainsBirthDate() {
            // given
            LoginId loginId = LoginId.of("user01");
            Password password = Password.of("ab19990322!");
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));

            // when
            assertThrows(CoreException.class, () ->
                userService.signUp(
                    loginId,
                    password,
                    "김철수",
                    birthDate,
                    Email.of("user@example.com")
                )
            );

            // then
            verify(userRepository, never()).save(any(UserModel.class));
        }

    }
}
