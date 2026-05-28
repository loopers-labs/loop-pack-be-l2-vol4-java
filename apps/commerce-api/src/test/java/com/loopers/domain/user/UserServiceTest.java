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
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String DEFAULT_USER_ID = "usertest123";
    private static final String DEFAULT_PASSWORD = "abc123!@#";
    private static final String ENCODED_PASSWORD = "encodedPassword";
    private static final String DEFAULT_NAME = "홍길동";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1995, 6, 10);
    private static final String DEFAULT_EMAIL = "test@naver.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserEntity savedUser() {
        return UserEntity.of(1L, DEFAULT_USER_ID, DEFAULT_NAME, DEFAULT_EMAIL,
                ENCODED_PASSWORD, DEFAULT_BIRTH_DATE, ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @DisplayName("[ECP] 유효한 인자가 주어지면 id가 할당된 회원이 생성된다.")
        @Test
        void createsUser_whenValidArgumentsAreProvided() {
            // arrange
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.empty());
            given(passwordEncoder.encode(DEFAULT_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any())).willReturn(savedUser());

            // act
            UserEntity result = userService.signup(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(DEFAULT_USER_ID, result.getUserId()),
                    () -> assertEquals(DEFAULT_NAME, result.getName()),
                    () -> assertEquals(DEFAULT_BIRTH_DATE, result.getBirthDate()),
                    () -> assertEquals(DEFAULT_EMAIL, result.getEmail())
            );
            verify(userRepository).findByUserId(DEFAULT_USER_ID);
            verify(userRepository).save(any());
        }

        @DisplayName("[ECP] 이미 존재하는 userId로 회원가입 시도하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenUserIdAlreadyExists() {
            // arrange
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.signup(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
            verify(userRepository).findByUserId(DEFAULT_USER_ID);
        }

        @DisplayName("[새 동작] 비밀번호에 허용되지 않는 문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.signup(DEFAULT_USER_ID, "한글Password1!", DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    @DisplayName("회원 정보 조회")
    @Nested
    class GetUser {

        @DisplayName("[ECP] 존재하는 userId와 올바른 비밀번호로 조회하면 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenValidArgumentsAreProvided() {
            // arrange
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches(DEFAULT_PASSWORD, ENCODED_PASSWORD)).willReturn(true);

            // act
            UserEntity result = userService.getUser(DEFAULT_USER_ID, DEFAULT_PASSWORD);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(DEFAULT_USER_ID, result.getUserId()),
                    () -> assertEquals(DEFAULT_NAME, result.getName()),
                    () -> assertEquals(DEFAULT_BIRTH_DATE, result.getBirthDate()),
                    () -> assertEquals(DEFAULT_EMAIL, result.getEmail())
            );
            verify(userRepository).findByUserId(DEFAULT_USER_ID);
        }

        @DisplayName("[ECP] 잘못된 비밀번호로 조회 시도하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsWrong() {
            // arrange
            String wrongPassword = "wrongpw123!@#";
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches(wrongPassword, ENCODED_PASSWORD)).willReturn(false);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.getUser(DEFAULT_USER_ID, wrongPassword));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[ECP] 존재하지 않는 userId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserNotFound() {
            // arrange
            String notExistId = "notExist123";
            given(userRepository.findByUserId(notExistId)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.getUser(notExistId, DEFAULT_PASSWORD));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
            verify(userRepository).findByUserId(notExistId);
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    class ChangePassword {

        @DisplayName("[ECP] 올바른 현재 비밀번호와 새 비밀번호가 주어지면 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenValidArgumentsAreProvided() {
            // arrange
            String newPassword = "newpwd123!@#";
            String newEncodedPassword = "newEncodedPassword";
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches(DEFAULT_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(passwordEncoder.matches(newPassword, ENCODED_PASSWORD)).willReturn(false);
            given(passwordEncoder.encode(newPassword)).willReturn(newEncodedPassword);
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            UserEntity result = userService.changePassword(DEFAULT_USER_ID, DEFAULT_PASSWORD, newPassword);

            // assert
            assertEquals(newEncodedPassword, result.getPassword());
            verify(userRepository).findByUserId(DEFAULT_USER_ID);
            verify(userRepository).save(any());
        }

        @DisplayName("[ECP] 현재 비밀번호가 일치하지 않는 경우 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            String wrongCurrentPassword = "wrongcurrent!@#";
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches(wrongCurrentPassword, ENCODED_PASSWORD)).willReturn(false);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.changePassword(DEFAULT_USER_ID, wrongCurrentPassword, "newpwd123!@#"));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[ECP] 새 비밀번호가 현재 비밀번호와 같은 경우 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches(DEFAULT_PASSWORD, ENCODED_PASSWORD)).willReturn(true);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.changePassword(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_PASSWORD));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[새 동작] 새 비밀번호에 허용되지 않는 문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNewPasswordContainsInvalidCharacters() {
            // arrange
            given(userRepository.findByUserId(DEFAULT_USER_ID)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches(DEFAULT_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(passwordEncoder.matches("한글Password1!", ENCODED_PASSWORD)).willReturn(false);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.changePassword(DEFAULT_USER_ID, DEFAULT_PASSWORD, "한글Password1!"));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }
}
