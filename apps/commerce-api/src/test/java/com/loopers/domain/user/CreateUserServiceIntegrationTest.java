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
class CreateUserServiceIntegrationTest {

    @Autowired
    private CreateUserService createUserService;

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

    @DisplayName("회원 가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("정상 회원가입 시 UserModel 의 모든 필드가 영속화된다")
        @Test
        void persistsAllFields_whenSignUpIsSuccessful() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            // when
            createUserService.signUp(loginId, loginPw, name, birthDate, email, gender);

            // then
            UserModel persisted = userRepository.findByLoginId(loginId).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getId()).isNotNull(),
                    () -> assertThat(persisted.getLoginId().value()).isEqualTo(loginId),
                    () -> assertThat(persisted.getName()).isEqualTo(name),
                    () -> assertThat(persisted.getBirthDate().value()).isEqualTo(birthDate),
                    () -> assertThat(persisted.getEmail().value()).isEqualTo(email),
                    () -> assertThat(persisted.getGender()).isEqualTo(gender),
                    () -> assertThat(persisted.matchesPassword(loginPw, passwordEncryptor)).isTrue()
            );
        }

        @DisplayName("이미 가입된 ID 로 회원가입 시도 시 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictException_whenDuplicateLoginIdIsProvided() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    createUserService.signUp(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
