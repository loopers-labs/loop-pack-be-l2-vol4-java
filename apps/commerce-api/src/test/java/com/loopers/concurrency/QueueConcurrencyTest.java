package com.loopers.concurrency;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 대기열 동시 진입 검증 — ZADD NX 의 원자성으로 동시 진입에도
 * 유실·중복 없이 전원이 서로 다른 순번을 받아야 한다.
 *
 * 게이트 off 면 대기열 API 가 거부되므로 게이트 on 으로 띄우되, 순번 단언의 결정성을 위해
 * 스케줄러 주기를 10분으로 늘린다(기동 직후 1회 tick 은 빈 큐 no-op, 테스트 중 재실행 없음).
 * DirtiesContext(AFTER_CLASS)로 컨텍스트를 닫아 휴면 스케줄러가 이후 테스트를 오염시키지 않게 한다.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
    "queue.order-gate.enabled=true",
    "queue.admission.interval-ms=600000"
})
class QueueConcurrencyTest {

    private static final int USERS = 30;
    private static final int ATTEMPTS = 20;

    private final QueueFacade queueFacade;
    private final WaitingQueueRepository waitingQueueRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    QueueConcurrencyTest(
        QueueFacade queueFacade,
        WaitingQueueRepository waitingQueueRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.queueFacade = queueFacade;
        this.waitingQueueRepository = waitingQueueRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 자기완결 클린업 — "기동 직후 1회 tick 은 빈 큐 no-op" 같은 스위트 순서 의존에 기대지 않는다
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private String loginId(int i) {
        return String.format("user%02d", i);
    }

    @DisplayName("서로 다른 30명이 동시에 진입하면, 유실·중복 없이 전원이 1~30 의 서로 다른 순번을 받는다.")
    @Test
    void assignsUniquePositions_underConcurrentEnter() throws InterruptedException {
        // arrange
        AtomicInteger success = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(USERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(USERS);

        // act
        for (int i = 1; i <= USERS; i++) {
            String loginId = loginId(i);
            executor.submit(() -> {
                try {
                    start.await(); // 동시 출발 보장
                    queueFacade.enter(loginId);
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 실패는 success 카운트 미달로 드러난다
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 최종 상태 기준: 인원 정확, 순번 1~30 전원 유일
        List<Long> positions = IntStream.rangeClosed(1, USERS)
            .mapToObj(i -> queueFacade.getPosition(loginId(i)).position())
            .toList();
        assertAll(
            () -> assertThat(finished).isTrue(),
            () -> assertThat(success.get()).isEqualTo(USERS),
            () -> assertThat(waitingQueueRepository.countWaiting()).isEqualTo(USERS),
            () -> assertThat(positions).doesNotHaveDuplicates(),
            () -> assertThat(positions)
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, USERS).boxed().toList())
        );
    }

    @DisplayName("같은 유저가 20번 동시에 진입해도, 대기열에는 1명만 존재하고 모든 응답 순번은 1 이다. (NX 멱등)")
    @Test
    void deduplicatesSameUser_underConcurrentEnter() throws InterruptedException {
        // arrange
        Queue<Long> positions = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ATTEMPTS);

        // act
        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    QueueInfo info = queueFacade.enter("sameUser");
                    positions.add(info.position());
                } catch (Exception ignored) {
                    // 실패는 positions 개수 미달로 드러난다
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        assertAll(
            () -> assertThat(finished).isTrue(),
            () -> assertThat(positions).hasSize(ATTEMPTS),
            () -> assertThat(positions).containsOnly(1L),
            () -> assertThat(waitingQueueRepository.countWaiting()).isEqualTo(1)
        );
    }
}
