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
}
