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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원을 등록할 때,")
    @Nested
    class Register {

        @DisplayName("회원가입 후 DB에 저장된 비밀번호는 평문이 아니다.")
        @Test
        void storedPasswordIsNotPlain() {
            // arrange
            String rawPassword = "Pass1234!";

            // act
            userService.register(
                new LoginId("loopers123"),
                rawPassword,
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com")
            );

            // assert
            User saved = userJpaRepository.findByLoginId(new LoginId("loopers123")).orElseThrow();
            assertThat(saved.getEncodedPassword()).isNotEqualTo(rawPassword);
        }

        @DisplayName("동일 email 로 동시에 가입 요청하면, 정확히 1건만 성공한다.") // DB가 정합성을 보장해주니 의미있는 테스트인가..?
        @Test
        void onlyOneSucceeds_whenSameEmailRegisteredConcurrently() throws Exception {
            // arrange
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        userService.register(
                            new LoginId("loopers" + idx),
                            "Pass1234!",
                            new Name("김민우"),
                            new Birth(LocalDate.of(1990, 1, 1)),
                            new Email("same@example.com")
                        );
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (DataIntegrityViolationException e) {
                        conflictCount.incrementAndGet();
                    } catch (Throwable ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(conflictCount.get()).isEqualTo(1),
                () -> assertThat(userJpaRepository.count()).isEqualTo(1)
            );
        }
    }

    @DisplayName("로그인할 때,")
    @Nested
    class Authenticate {

        @DisplayName("로그인 ID 와 비밀번호가 일치하면, 해당 사용자를 반환한다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            String rawPassword = "Pass1234!";
            User registered = userService.register(
                new LoginId("loopers123"),
                rawPassword,
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com")
            );

            // act
            User result = userService.authenticate(new LoginId("loopers123"), rawPassword);

            // assert
            assertThat(result.getId()).isEqualTo(registered.getId());
        }

        @DisplayName("존재하지 않는 로그인 ID 면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenUserNotFound() {
            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> userService.authenticate(new LoginId("notexist01"), "Pass1234!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            userService.register(
                new LoginId("loopers123"),
                "Pass1234!",
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com")
            );

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> userService.authenticate(new LoginId("loopers123"), "Wrong123!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
