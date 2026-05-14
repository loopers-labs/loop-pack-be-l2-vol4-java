package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @SpyBean
    private UserRepository userRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private PasswordHasher passwordHasher;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {
        @DisplayName("회원 가입시 User 저장이 수행된다")
        @Test
        void savesUser_whenSignUpIsRequested() {
            // arrange
            String loginId = "user01";

            // act
            userService.signUp(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE);

            // assert
            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("이미 가입된 ID 로 회원가입 시도 시 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictException_whenDuplicateLoginIdIsProvided() {
            // arrange
            String loginId = "user01";
            userJpaRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordHasher));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.signUp(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetMyInfo {

        @DisplayName("해당 ID 의 회원이 존재할 경우, 회원 정보가 반환된다")
        @Test
        void returnsUserInfo_whenUserExists() {
            // arrange
            String loginId = "user01";
            String password = "Password1!";
            userJpaRepository.save(new UserModel(loginId, password, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordHasher));

            // act
            UserModel result = userService.findMyInfo(loginId, password);

            // assert
            assertThat(result.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다")
        @Test
        void returnsNull_whenUserDoesNotExist() {
            // act
            UserModel result = userService.findMyInfo("nonexistent", "Password1!");

            // assert
            assertThat(result).isNull();
        }

        @DisplayName("헤더 loginPw 인증이 실패하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenLoginPwAuthenticationFails() {
            // arrange
            String loginId = "user01";
            userJpaRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordHasher));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.findMyInfo(loginId, "WrongPass1!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {
        @DisplayName("기존 비밀번호가 일치하고 신규 비밀번호가 유효하면 비밀번호가 변경된다")
        @Test
        void changesPassword_whenOldPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            String loginId = "user01";
            UserModel user = userJpaRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordHasher));

            // act
            userService.changePassword(loginId, "Password1!", "Password1!", "NewPass99!");

            // assert
            UserModel updated = userJpaRepository.findById(user.getId()).orElseThrow();
            assertThat(updated.matchesPassword("NewPass99!", passwordHasher)).isTrue();
        }

        @DisplayName("해당 loginId 의 회원이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFoundException_whenUserDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword("nonexistent", "Password1!", "Password1!", "NewPass99!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("헤더 loginPw 인증이 실패하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenLoginPwAuthenticationFails() {
            // arrange
            String loginId = "user01";
            userJpaRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordHasher));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(loginId, "WrongPass1!", "Password1!", "NewPass99!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenOldPasswordDoesNotMatch() {
            // arrange
            String loginId = "user01";
            userJpaRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordHasher));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(loginId, "Password1!", "WrongPass1!", "NewPass99!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
