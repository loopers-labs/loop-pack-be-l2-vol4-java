package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private WaitingQueue waitingQueue;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("가장 먼저 진입한 유저의 순번은 1이다.")
    @Test
    void firstEntrant_getsPosition1() {
        // act
        long position = queueService.enter("alice");

        // assert
        assertThat(position).isEqualTo(1L);
        assertThat(queueService.getWaitingCount()).isEqualTo(1L);
    }

    @DisplayName("두 번째로 진입한 유저의 순번은 2이고, 전체 대기 인원은 2다.")
    @Test
    void secondEntrant_getsPosition2_andCountIs2() {
        // arrange
        queueService.enter("alice");

        // act
        long position = queueService.enter("bob");

        // assert
        assertThat(position).isEqualTo(2L);
        assertThat(queueService.getWaitingCount()).isEqualTo(2L);
    }

    @DisplayName("같은 유저가 재진입해도 최초 순번과 전체 대기 인원은 변하지 않는다(ZADD NX).")
    @Test
    void duplicateEntry_keepsOriginalPositionAndCount() {
        // arrange
        queueService.enter("alice");
        queueService.enter("bob"); // alice=1, bob=2

        // act — alice 가 다시 진입(새로고침/재접속)
        long positionAfterReenter = queueService.enter("alice");

        // assert
        assertThat(positionAfterReenter).isEqualTo(1L);
        assertThat(queueService.getWaitingCount()).isEqualTo(2L);
    }

    @DisplayName("대기열에 진입한 적 없는 유저의 순번 조회는 NOT_FOUND 예외다.")
    @Test
    void position_ofNonEntrant_throwsNotFound() {
        // act & assert
        assertThatThrownBy(() -> queueService.getPosition("ghost"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @DisplayName("N 명이 동시에 진입해도 최종 순번은 1..N 로 유일하게(중복·누락 없이) 부여된다.")
    @Test
    void concurrentEntry_assignsUniqueSequentialPositions() throws InterruptedException {
        // arrange
        int n = 50;
        // 각 태스크가 start.await() 에서 대기하므로, 모든 태스크가 동시에 떠야 데드락이 없다(풀 크기 == 태스크 수).
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);

        // act — n 명이 동시에 진입
        for (int i = 0; i < n; i++) {
            String loginId = "user-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    queueService.enter(loginId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // assert — 진입 완료 후 각 유저의 최종 순번을 모으면 정확히 {1..N}
        assertThat(queueService.getWaitingCount()).isEqualTo(n);
        Set<Long> finalPositions = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < n; i++) {
            finalPositions.add(queueService.getPosition("user-" + i));
        }
        assertThat(finalPositions)
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, n).boxed().toList());
    }

    @DisplayName("pollFirst 는 진입 순서(score 오름차순)대로 앞에서 꺼내고 대기열에서 제거한다.")
    @Test
    void pollFirst_returnsInEntryOrder_andRemoves() {
        // arrange
        List.of("alice", "bob", "carol").forEach(queueService::enter);

        // act — 앞 2명 꺼내기
        List<String> polled = waitingQueue.pollFirst(2);

        // assert
        assertThat(polled).containsExactly("alice", "bob");
        assertThat(queueService.getWaitingCount()).isEqualTo(1L);
        assertThat(queueService.getPosition("carol")).isEqualTo(1L);
    }
}