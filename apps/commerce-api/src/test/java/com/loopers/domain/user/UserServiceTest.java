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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_EMAIL = "test@example.com";

    private UserModel createUser() {
        return new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 가입하면, 저장된 회원 정보를 반환한다.")
        @Test
        void returnsUserModel_whenAllFieldsAreValid() {
            // arrange
            UserModel user = createUser();
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.empty());
            given(userRepository.save(any(UserModel.class))).willReturn(user);

            // act
            UserModel result = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(VALID_NAME)
            );
            then(userRepository).should().save(any(UserModel.class));
        }

        @DisplayName("이미 가입된 로그인 ID이면, CONFLICT 예외가 발생하고 save는 호출되지 않는다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(createUser()));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            then(userRepository).should(never()).save(any());
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("유효한 로그인 ID와 비밀번호로 조회하면, 회원 정보를 반환한다.")
        @Test
        void returnsUserModel_whenCredentialsMatch() {
            // arrange
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(createUser()));

            // act
            UserModel result = userService.getUser(VALID_LOGIN_ID, VALID_PASSWORD);

            // assert
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            given(userRepository.findByLoginId(any())).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.getUser("nonexistent", VALID_PASSWORD)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(createUser()));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.getUser(VALID_LOGIN_ID, "WrongPass1!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면, 정상적으로 변경된다.")
        @Test
        void changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            given(userRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(createUser()));

            // act & assert
            assertDoesNotThrow(() ->
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, "NewPass2@")
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 변경 요청하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            given(userRepository.findByLoginId(any())).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword("nonexistent", VALID_PASSWORD, "NewPass2@")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
