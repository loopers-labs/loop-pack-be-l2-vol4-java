package com.loopers.domain.user;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("유효한 loginId와 password가 주어지면, 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidCredentialsAreProvided() {
            // arrange
            UserModel saved = userRepository.save(new UserModel(
                "user01",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            ));

            // act
            UserModel result = userService.getUser("user01", "Password1!");

            // assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo(saved.getLoginId()),
                () -> assertThat(result.getName()).isEqualTo(saved.getName()),
                () -> assertThat(result.getBirthDate()).isEqualTo(saved.getBirthDate()),
                () -> assertThat(result.getEmail()).isEqualTo(saved.getEmail())
            );
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // arrange
            String notExistLoginId = "unknown";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getUser(notExistLoginId, "Password1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordNotMatches() {
            // arrange
            userRepository.save(new UserModel(
                "user01",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            ));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getUser("user01", "WrongPassword!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
