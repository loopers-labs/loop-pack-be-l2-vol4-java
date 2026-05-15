package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 정보가 주어지면, 정상적으로 가입된다.")
        @Test
        void registersMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testUser1";

            // Act
            Member member = memberService.register(loginId, "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Assert
            assertThat(member.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // Arrange
            memberService.register("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.register("testUser1", "Password2@", "김철수", "1995-05-05", "other@example.com")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 정보를 조회할 때, ")
    @Nested
    class GetMe {

        @DisplayName("올바른 loginId와 비밀번호가 주어지면, 회원 정보를 반환한다.")
        @Test
        void returnsMember_whenCredentialsAreValid() {
            // Arrange
            memberService.register("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Act
            Member result = memberService.getMe("testUser1", "Password1!");

            // Assert
            assertThat(result.getLoginId()).isEqualTo("testUser1");
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.getMe("notExist", "Password1!")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // Arrange
            memberService.register("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.getMe("testUser1", "WrongPassword1!")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 정보가 주어지면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenCredentialsAreValid() {
            // Arrange
            memberService.register("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Act
            memberService.changePassword("testUser1", "Password1!", "NewPassword2@");

            // Assert
            Member member = memberService.getMe("testUser1", "NewPassword2@");
            assertThat(member.getLoginId()).isEqualTo("testUser1");
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.changePassword("notExist", "Password1!", "NewPassword2@")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

    }
}
