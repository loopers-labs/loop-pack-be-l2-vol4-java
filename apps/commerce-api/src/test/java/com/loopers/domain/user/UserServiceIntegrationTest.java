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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class SignUp {

        @DisplayName("정상 정보로 회원 가입 시, User 저장이 수행된다.")
        @Test
        void savesUser_whenValidInfoProvided() {
            // arrange
            String loginId = "testId";
            String password = "validPassword123";
            String name = "임찬빈";
            String birthDate = "1998-04-11";
            String email = "test@test.com";

            // act
            UserModel result = userService.signUp(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                () -> verify(userRepository, times(1)).save(any(UserModel.class)),
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(loginId),
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getEmail()).isEqualTo(email),
                () -> assertThat(result.getPassword()).isNotEqualTo(password)   // 암호화되어 평문과 달라야 함
            );
        }

        @DisplayName("이미 가입된 ID로 회원 가입 시도 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            String duplicatedLoginId = "testId";
            userService.signUp(duplicatedLoginId, "validPassword123", "임찬빈", "1998-04-11", "test@test.com");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.signUp(duplicatedLoginId, "anotherPassword456", "다른사람", "2000-01-01", "other@test.com"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
