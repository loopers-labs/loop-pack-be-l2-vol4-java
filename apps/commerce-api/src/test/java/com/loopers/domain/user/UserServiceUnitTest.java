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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입을 할 때")
    @Nested
    class CreateUser {

        @DisplayName("정상 입력이면, 비밀번호를 암호화하여 저장한다.")
        @Test
        void encodesPasswordAndSaves_whenValidInput() {
            // given
            String loginId = "minbo";
            String rawPassword = "Test1234!";
            String name = "민보";
            LocalDate birthDate = LocalDate.of(1991, 8, 21);
            String email = "test@example.com";
            String encodedPassword = "ENCODED";

            given(userRepository.existsByLoginId(loginId)).willReturn(false);
            given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            UserModel result = userService.createUser(loginId, rawPassword, name, birthDate, email);

            // then
            verify(passwordEncoder).encode(rawPassword);
            verify(userRepository).save(any(UserModel.class));
            assertThat(result.getLoginId()).isEqualTo(loginId);
            assertThat(result.getPassword()).isEqualTo(encodedPassword);
        }

        @DisplayName("이미 사용중인 loginId가 존재한다면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdExists() {
            // given
            given(userRepository.existsByLoginId("minbo")).willReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.createUser("minbo", "Pass1234!", "민보", LocalDate.of(2000, 1, 1), "min@example.com")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any());
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // given
            String passwordContainingBirth = "P20000101!";
            LocalDate birthDate = LocalDate.of(2000, 1, 1);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.createUser("minbo", passwordContainingBirth, "민보", birthDate, "min@example.com")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).existsByLoginId(anyString());
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any());
        }
    }
}
