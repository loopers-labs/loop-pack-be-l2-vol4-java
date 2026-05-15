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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class FindUserServiceIntegrationTest {

    @Autowired
    private FindUserService findUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("로그인 유저를 조회할 때,")
    @Nested
    class GetLoginUser {

        @DisplayName("아이디/비밀번호가 일치하면 UserModel 이 반환된다")
        @Test
        void returnsUserModel_whenLoginIdAndLoginPwAreValid() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            UserModel saved = userRepository.save(new UserModel(loginId, loginPw, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            UserModel result = findUserService.getLoginUser(loginId, loginPw);

            // then
            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("해당 ID 의 회원이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFoundException_whenUserDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    findUserService.getLoginUser("nonexistent", "Password1!")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenLoginPwAuthenticationFails() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    findUserService.getLoginUser(loginId, "WrongPass1!")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
