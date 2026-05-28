package com.loopers.domain.user;

import com.loopers.application.user.UserService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("회원가입 후 저장된 비밀번호는 평문과 다르다.")
        @Test
        void password_isNotStoredAsPlainText_afterSignUp() {
            // arrange
            String rawPassword = "Password1!";
            UserModel user = new UserModel("user01", rawPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            UserModel saved = userService.signUp(user);

            // assert
            assertThat(saved.getPassword()).isNotEqualTo(rawPassword);
        }

        @DisplayName("유효한 정보가 주어지면, 유저가 저장된다.")
        @Test
        void savesUser_whenValidUserInfoIsProvided() {
            // arrange
            UserModel user = new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            UserModel saved = userService.signUp(user);

            // assert
            assertThat(saved)
                .satisfies(u -> assertThat(u.getId()).isNotNull())
                .satisfies(u -> assertThat(u.getLoginId()).isEqualTo("user01"))
                .satisfies(u -> assertThat(u.getName()).isEqualTo("홍길동"))
                .satisfies(u -> assertThat(u.getEmail()).isEqualTo("user@example.com"));
        }

        @DisplayName("동일 loginId로 동시에 가입 요청 시, 한 건만 성공하고 나머지는 CONFLICT 예외가 발생한다.")
        @Test
        void onlyOneSucceeds_whenSameLoginIdSubmittedConcurrently() throws Exception {
            // arrange
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            List<Throwable> unexpectedExceptions = new ArrayList<>();

            // act
            List<Future<?>> futures = List.of(
                executor.submit(() -> {
                    try {
                        latch.await();
                        userService.signUp(new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com"));
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedExceptions.add(e);
                    }
                }),
                executor.submit(() -> {
                    try {
                        latch.await();
                        userService.signUp(new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com"));
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedExceptions.add(e);
                    }
                })
            );
            latch.countDown();
            for (Future<?> f : futures) f.get();
            executor.shutdown();

            // assert
            assertThat(unexpectedExceptions).isEmpty();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(1);
        }

        @DisplayName("동시 가입 중 예상치 못한 예외가 발생하면, 무시되지 않고 감지되어야 한다.")
        @Test
        void failsTest_whenUnexpectedExceptionOccursInConcurrentThread() throws Exception {
            // arrange
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch latch = new CountDownLatch(1);
            List<Throwable> unexpectedExceptions = new ArrayList<>();

            // act
            Future<?> future = executor.submit(() -> {
                try {
                    latch.await();
                    throw new RuntimeException("의도적 예외");
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT) {}
                } catch (Exception e) {
                    unexpectedExceptions.add(e); // 수집
                }
            });
            latch.countDown();
            future.get();
            executor.shutdown();

            // assert - 예외가 감지되어야 하므로 비어있으면 안 됨
            assertThat(unexpectedExceptions).isNotEmpty();
        }

        @DisplayName("이미 존재하는 email로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsException_whenEmailAlreadyExists() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            UserModel duplicateEmailUser = new UserModel(
                "user02", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "user@example.com"
            );

            // act & assert
            assertThatThrownBy(() -> userService.signUp(duplicateEmailUser))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.CONFLICT);
                });
        }

        @DisplayName("이미 존재하는 loginId로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            UserModel duplicateUser = new UserModel(
                "user01", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            );

            // act & assert
            assertThatThrownBy(() -> userService.signUp(duplicateUser))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.CONFLICT);
                    assertThat(ce.getCause()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                    assertThat(ce.getMessage()).doesNotContain("user01");
                    assertThat(ce.getLogMessage()).contains("user01");
                });
        }
    }

    @DisplayName("ID로 유저를 조회할 때,")
    @Nested
    class GetUserById {

        @DisplayName("유효한 userId가 주어지면, 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidUserIdIsProvided() {
            // arrange
            UserModel saved = userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            UserModel result = userService.getUserById(saved.getId());

            // assert
            assertThat(result)
                .satisfies(u -> assertThat(u.getId()).isEqualTo(saved.getId()))
                .satisfies(u -> assertThat(u.getLoginId()).isEqualTo("user01"));
        }

        @DisplayName("존재하지 않는 userId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdNotFound() {
            // arrange
            Long nonExistentUserId = 999L;

            // act & assert
            assertThatThrownBy(() -> userService.getUserById(nonExistentUserId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("유효한 loginId와 password가 주어지면, 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidCredentialsAreProvided() {
            // arrange
            UserModel saved = userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            UserModel result = userService.getUser("user01", "Password1!");

            // assert
            assertThat(result)
                .satisfies(u -> assertThat(u.getLoginId()).isEqualTo(saved.getLoginId()))
                .satisfies(u -> assertThat(u.getName()).isEqualTo(saved.getName()))
                .satisfies(u -> assertThat(u.getBirthDate()).isEqualTo(saved.getBirthDate()))
                .satisfies(u -> assertThat(u.getEmail()).isEqualTo(saved.getEmail()));
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // arrange
            String notExistLoginId = "unknown";

            // act & assert
            assertThatThrownBy(() -> userService.getUser(notExistLoginId, "Password1!"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordNotMatches() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act & assert
            assertThatThrownBy(() ->
                userService.getUser("user01", "WrongPassword!")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("비밀번호 변경 후 저장된 비밀번호는 새 평문과 다르다.")
        @Test
        void password_isNotStoredAsPlainText_afterUpdate() {
            // arrange
            String newRawPassword = "NewPassword1!";
            UserModel saved = userService.signUp(new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com"));

            // act
            userService.updatePassword(saved.getId(), "Password1!", newRawPassword);

            // assert
            UserModel updated = userRepository.findByLoginId("user01").get();
            assertThat(updated.getPassword()).isNotEqualTo(newRawPassword);
        }

        @DisplayName("기존 비밀번호가 일치하면, 비밀번호가 변경된다.")
        @Test
        void updatesPassword_whenOldPasswordMatches() {
            // arrange
            UserModel saved = userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            userService.updatePassword(saved.getId(), "Password1!", "NewPassword1!");

            // assert
            UserModel updated = userService.getUser("user01", "NewPassword1!");
            assertThat(updated).isNotNull();
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenOldPasswordNotMatches() {
            // arrange
            UserModel saved = userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act & assert
            assertThatThrownBy(() ->
                userService.updatePassword(saved.getId(), "WrongPassword!", "NewPassword1!")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            UserModel saved = userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act & assert
            assertThatThrownBy(() ->
                userService.updatePassword(saved.getId(), "Password1!", "Password1!")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
