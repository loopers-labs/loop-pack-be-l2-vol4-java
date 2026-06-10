package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserFacadeIntegrationTest {

    @Autowired
    private UserFacade userFacade;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private SignUpCommand command(String loginId) {
        return new SignUpCommand(loginId, "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M);
    }

    @DisplayName("회원 가입을 할 때, ")
    @Nested
    class SignUp {

        @DisplayName("정상 입력이면, User 저장이 수행되고 저장된 정보가 반환된다.")
        @Test
        void persistsUser_whenInputIsValid() {
            // act
            UserInfo info = userFacade.signUp(command("tester01"));

            // assert
            assertAll(
                () -> verify(userRepository).save(any(UserModel.class)),
                () -> assertThat(info.id()).isPositive(),
                () -> assertThat(info.loginId()).isEqualTo("tester01")
            );
        }

        @DisplayName("이미 가입된 로그인 ID 가 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userFacade.signUp(command("tester01"));

            // act
            CoreException ex = assertThrows(CoreException.class, () -> userFacade.signUp(
                new SignUpCommand("tester01", "Password2@", "김철수", "1991-06-15", "another@example.com", Gender.F)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("회원 가입 시 비밀번호는 평문이 아닌 해시로 저장된다.")
        @Test
        void storesHashedPassword() {
            // act
            userFacade.signUp(command("tester01"));

            // assert
            UserModel reloaded = userRepository.findByLoginId("tester01").orElseThrow();
            assertAll(
                () -> assertThat(reloaded.getPassword()).isNotEqualTo("Password1!"),
                () -> assertThat(reloaded.matchesPassword("Password1!")).isTrue()
            );
        }
    }

    @DisplayName("내 정보를 조회할 때, ")
    @Nested
    class GetMyInfo {

        @DisplayName("로그인 ID·비밀번호가 일치하면, 회원 정보가 반환된다.")
        @Test
        void returnsUser_whenCredentialsMatch() {
            // arrange
            userFacade.signUp(command("tester01"));

            // act
            Optional<UserInfo> result = userFacade.getMyInfo("tester01", "Password1!");

            // assert
            assertAll(
                () -> assertThat(result).isPresent(),
                () -> assertThat(result.get().loginId()).isEqualTo("tester01"),
                () -> assertThat(result.get().name()).isEqualTo("홍길동")
            );
        }

        @DisplayName("존재하지 않는 로그인 ID 면, Optional.empty 가 반환된다.")
        @Test
        void returnsEmpty_whenLoginIdNotFound() {
            // act
            Optional<UserInfo> result = userFacade.getMyInfo("nonexistent", "Password1!");

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("정상 입력이면, 저장된 비밀번호가 새 값으로 갱신된다.")
        @Test
        void persistsNewPassword_whenInputIsValid() {
            // arrange
            userFacade.signUp(command("tester01"));

            // act
            userFacade.changePassword("tester01", "Password1!", "NewPass2@");

            // assert
            UserModel reloaded = userRepository.findByLoginId("tester01").orElseThrow();
            assertAll(
                () -> assertThat(reloaded.matchesPassword("NewPass2@")).isTrue(),
                () -> assertThat(reloaded.matchesPassword("Password1!")).isFalse()
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordMismatch() {
            // arrange
            userFacade.signUp(command("tester01"));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> userFacade.changePassword("tester01", "WrongPw1!", "NewPass2@"));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
