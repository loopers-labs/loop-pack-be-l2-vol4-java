package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest
class UserConcurrentRegisterTest {

    private final UserService userService;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserConcurrentRegisterTest(
        UserService userService,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.userService = userService;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
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
                        new Name("김민우"),
                        new Birth(LocalDate.of(1990, 1, 1)),
                        new Email("same@example.com"),
                        "Pass1234!"
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
