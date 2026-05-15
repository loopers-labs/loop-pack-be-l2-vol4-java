package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserFacadeIntegrationTest {

    private final UserFacade userFacade;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserFacadeIntegrationTest(
        UserFacade userFacade,
        UserJpaRepository userJpaRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.userFacade = userFacade;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("정상 변경 시, DB 의 password 가 새 비밀번호의 BCrypt 인코딩으로 교체된다.")
        @Test
        void replacesPasswordInDb_whenChangedSuccessfully() {
            // given
            String currentRaw = "Abcd1234!";
            String newRaw = "Xyz!9876@";
            Long userId = userFacade.signUp(new UserCommand.SignUp(
                "user01", currentRaw, "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            )).id();

            // when
            userFacade.changePassword(userId, new UserCommand.ChangePassword(currentRaw, newRaw));

            // then
            UserModel saved = userJpaRepository.findById(userId).orElseThrow();
            String storedEncoded = saved.getPassword().getValue();
            assertAll(
                () -> assertThat(passwordEncoder.matches(newRaw, storedEncoded)).isTrue(),
                () -> assertThat(passwordEncoder.matches(currentRaw, storedEncoded)).isFalse()
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 가 발생하고 DB 의 password 는 변경되지 않는다.")
        @Test
        void preservesPassword_whenNewIsSameAsCurrent() {
            // given
            String currentRaw = "Abcd1234!";
            Long userId = userFacade.signUp(new UserCommand.SignUp(
                "user01", currentRaw, "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            )).id();
            String beforeEncoded = userJpaRepository.findById(userId).orElseThrow().getPassword().getValue();

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                userFacade.changePassword(userId, new UserCommand.ChangePassword(currentRaw, currentRaw))
            );

            // then
            String afterEncoded = userJpaRepository.findById(userId).orElseThrow().getPassword().getValue();
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(afterEncoded).isEqualTo(beforeEncoded)
            );
        }

        @DisplayName("현재 비밀번호가 DB 와 일치하지 않으면, UNAUTHORIZED 가 발생하고 DB 의 password 는 변경되지 않는다.")
        @Test
        void preservesPassword_whenCurrentPasswordIsWrong() {
            // given
            String currentRaw = "Abcd1234!";
            String wrongCurrentRaw = "Wrong9999!";
            String newRaw = "Xyz!9876@";
            Long userId = userFacade.signUp(new UserCommand.SignUp(
                "user01", currentRaw, "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            )).id();
            String beforeEncoded = userJpaRepository.findById(userId).orElseThrow().getPassword().getValue();

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                userFacade.changePassword(userId, new UserCommand.ChangePassword(wrongCurrentRaw, newRaw))
            );

            // then
            String afterEncoded = userJpaRepository.findById(userId).orElseThrow().getPassword().getValue();
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
                () -> assertThat(afterEncoded).isEqualTo(beforeEncoded)
            );
        }
    }
}
