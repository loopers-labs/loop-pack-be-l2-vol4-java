package com.loopers.application.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class WaitingQueueIntegrationTest {

    private final WaitingQueueFacade waitingQueueFacade;
    private final WaitingQueueAdmitService waitingQueueAdmitService;
    private final WaitingQueueRepository waitingQueueRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    WaitingQueueIntegrationTest(
        WaitingQueueFacade waitingQueueFacade,
        WaitingQueueAdmitService waitingQueueAdmitService,
        WaitingQueueRepository waitingQueueRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.waitingQueueFacade = waitingQueueFacade;
        this.waitingQueueAdmitService = waitingQueueAdmitService;
        this.waitingQueueRepository = waitingQueueRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("동시에 여러 회원이 대기열에 진입해도 모든 회원에게 고유한 순번이 부여된다.")
    @Test
    void assignsUniquePositions_whenUsersEnterConcurrently() throws InterruptedException {
        // arrange
        int threadCount = 20;

        // act
        List<Throwable> failures = runConcurrently(threadCount, index -> {
            waitingQueueFacade.enter("user" + index);
        });

        // assert
        List<Long> assignedPositions = IntStream.range(0, threadCount)
            .mapToObj(index -> waitingQueueFacade.position("user" + index))
            .map(WaitingQueueInfo.Position::position)
            .sorted()
            .toList();
        assertAll(
            () -> assertThat(failures).isEmpty(),
            () -> assertThat(waitingQueueRepository.countWaitingUsers()).isEqualTo(threadCount),
            () -> assertThat(assignedPositions).containsExactlyElementsOf(
                IntStream.rangeClosed(1, threadCount)
                    .mapToObj(Long::valueOf)
                    .toList()
            )
        );
    }

    @DisplayName("대기열 순번은 사용자 ID 사전순이 아니라 진입 순서대로 부여된다.")
    @Test
    void preservesInsertionOrder_whenUserIdsHaveReverseLexicalOrder() {
        // arrange
        waitingQueueRepository.enqueueIfAbsent("z-user");
        waitingQueueRepository.enqueueIfAbsent("a-user");

        // act
        List<String> admittedUsers = waitingQueueRepository.popWaitingUsers(2);

        // assert
        assertThat(admittedUsers).containsExactly("z-user", "a-user");
    }

    @DisplayName("입장 토큰은 TTL이 지나면 만료된다.")
    @Test
    void expiresEntryToken_whenTtlPasses() throws InterruptedException {
        // arrange
        waitingQueueRepository.issueEntryToken("user1234", "entry-token", Duration.ofMillis(100));

        // act
        Optional<String> token = waitUntilTokenExpired("user1234");

        // assert
        assertThat(token).isEmpty();
    }

    @DisplayName("스케줄러는 배치 크기만큼만 입장 토큰을 발급하고 나머지는 대기열에 남긴다.")
    @Test
    void admitsOnlyBatchSize_whenWaitingUsersExceedBatchSize() {
        // arrange
        int waitingUsers = 25;
        int expectedAdmittedUsers = 18;
        for (int index = 0; index < waitingUsers; index++) {
            waitingQueueRepository.enqueueIfAbsent("user" + index);
        }

        // act
        int admittedUsers = waitingQueueAdmitService.admit();

        // assert
        assertAll(
            () -> assertThat(admittedUsers).isEqualTo(expectedAdmittedUsers),
            () -> assertThat(waitingQueueRepository.countWaitingUsers()).isEqualTo(waitingUsers - expectedAdmittedUsers),
            () -> assertThat(IntStream.range(0, expectedAdmittedUsers)
                .mapToObj(index -> waitingQueueRepository.findEntryToken("user" + index))
                .allMatch(Optional::isPresent)).isTrue(),
            () -> assertThat(IntStream.range(expectedAdmittedUsers, waitingUsers)
                .mapToObj(index -> waitingQueueRepository.findEntryToken("user" + index))
                .allMatch(Optional::isEmpty)).isTrue()
        );
    }

    private Optional<String> waitUntilTokenExpired(String userLoginId) throws InterruptedException {
        Optional<String> token = waitingQueueRepository.findEntryToken(userLoginId);
        for (int attempt = 0; attempt < 20 && token.isPresent(); attempt++) {
            Thread.sleep(100);
            token = waitingQueueRepository.findEntryToken(userLoginId);
        }
        return token;
    }

    private List<Throwable> runConcurrently(int threadCount, ConcurrentTask task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int index = 0; index < threadCount; index++) {
                final int taskIndex = index;
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        task.run(taskIndex);
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();

            return failures;
        } finally {
            executorService.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConcurrentTask {
        void run(int index);
    }
}
