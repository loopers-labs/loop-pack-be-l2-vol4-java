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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final String VALID_LOGIN_ID = "chanhee";
    private static final String VALID_RAW_PASSWORD = "chan1234!";
    private static final String VALID_NAME = "김찬희";
    private static final String VALID_BIRTH_DATE = "1995-05-10";
    private static final String VALID_EMAIL = "chan950510@gmail.com";

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 처리 시")
    @Nested
    class SignUp {

        @DisplayName("새로운 사용자가 정상적으로 저장되고 비밀번호는 인코딩된 채 보관된다.")
        @Test
        void persistsUser_whenRequestIsValid() {
            // act
            UserModel saved = userService.signUp(
                VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isPositive(),
                () -> assertThat(saved.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(saved.getPassword().value()).isNotEqualTo(VALID_RAW_PASSWORD),
                () -> assertThat(saved.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(saved.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(saved.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 재가입 시 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
