package com.loopers.application.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserApplicationServiceIntegrationTest {

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void createUser(String loginId) {
        userApplicationService.signup(loginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), loginId + "@test.com");
    }

    @DisplayName("인증")
    @Nested
    class Authenticate {

        @DisplayName("[ECP] 유효한 자격증명으로 인증 시 userId를 반환한다.")
        @Test
        void returnsUserId_whenCredentialsAreValid() {
            // arrange
            createUser("testuser");

            // act
            String userId = userApplicationService.authenticate("testuser", "Password1!");

            // assert
            assertNotNull(userId);
        }

        @DisplayName("[ECP] 존재하지 않는 loginId로 인증 시 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> userApplicationService.authenticate("notexist", "Password1!"));
            assertEquals(ErrorType.UNAUTHORIZED, exception.getErrorType());
        }

        @DisplayName("[ECP] 잘못된 비밀번호로 인증 시 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            createUser("testuser");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> userApplicationService.authenticate("testuser", "WrongPass1!"));
            assertEquals(ErrorType.UNAUTHORIZED, exception.getErrorType());
        }
    }
}
