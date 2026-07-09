package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueRedisRepositoryIntegrationTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("대기열 진입 — ZADD NX 멱등")
    class Enter {

        @Test
        @DisplayName("신규 진입은 true, 진입 순서대로 0-based 순번을 갖는다.")
        void assignsRankByEntryOrder() {
            assertThat(waitingQueueRepository.enter(1L, 100L)).isTrue();
            assertThat(waitingQueueRepository.enter(2L, 200L)).isTrue();
            assertThat(waitingQueueRepository.enter(3L, 300L)).isTrue();

            assertThat(waitingQueueRepository.rank(1L)).contains(0L);
            assertThat(waitingQueueRepository.rank(2L)).contains(1L);
            assertThat(waitingQueueRepository.rank(3L)).contains(2L);
            assertThat(waitingQueueRepository.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("재진입은 false 를 반환하고 최초 진입 시각을 보존한다 — 순번이 뒤로 밀리지 않는다.")
        void reentryKeepsOriginalRank() {
            waitingQueueRepository.enter(1L, 100L);
            waitingQueueRepository.enter(2L, 200L);

            boolean reentered = waitingQueueRepository.enter(1L, 999L); // 더 늦은 시각으로 재진입 시도

            assertThat(reentered).isFalse();
            assertThat(waitingQueueRepository.rank(1L)).contains(0L); // score 덮어썼다면 1 이 됐을 것
            assertThat(waitingQueueRepository.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("대기열에 없는 유저의 순번은 empty 다.")
        void rankOfAbsentUserIsEmpty() {
            waitingQueueRepository.enter(1L, 100L);

            assertThat(waitingQueueRepository.rank(99L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("popFront — 앞에서부터 순서대로 꺼내기")
    class PopFront {

        @Test
        @DisplayName("진입 순서 그대로 꺼내고, 꺼낸 유저는 대기열에서 빠지며 남은 유저 순번이 당겨진다.")
        void popsInEntryOrderAndShiftsRemaining() {
            waitingQueueRepository.enter(1L, 100L);
            waitingQueueRepository.enter(2L, 200L);
            waitingQueueRepository.enter(3L, 300L);

            List<Long> popped = waitingQueueRepository.popFront(2);

            assertThat(popped).containsExactly(1L, 2L);
            assertThat(waitingQueueRepository.size()).isEqualTo(1);
            assertThat(waitingQueueRepository.rank(3L)).contains(0L);
        }

        @Test
        @DisplayName("대기 인원보다 큰 count 는 있는 만큼만, 빈 대기열은 빈 목록을 반환한다.")
        void popMoreThanSizeReturnsAllThenEmpty() {
            waitingQueueRepository.enter(1L, 100L);

            assertThat(waitingQueueRepository.popFront(10)).containsExactly(1L);
            assertThat(waitingQueueRepository.popFront(10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시 진입")
    class ConcurrentEnter {

        @Test
        @DisplayName("N명이 동시에 진입해도 전원이 무중복 순번(0..N-1)을 받는다.")
        void concurrentEntriesGetDistinctRanks() throws InterruptedException {
            int userCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(userCount);

            for (int i = 1; i <= userCount; i++) {
                long userId = i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        waitingQueueRepository.enter(userId, System.currentTimeMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(doneGate.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            // 진입 중 순번은 같은 ms 진입자의 사전순 tie-break 로 흔들릴 수 있으므로,
            // 전원 진입이 끝난 뒤의 스냅샷으로 불변식(전원 포함 + 무중복 0..N-1)을 검증한다
            assertThat(waitingQueueRepository.size()).isEqualTo(userCount);
            Set<Long> ranks = ConcurrentHashMap.newKeySet();
            for (long userId = 1; userId <= userCount; userId++) {
                waitingQueueRepository.rank(userId).ifPresent(ranks::add);
            }
            assertThat(ranks).hasSize(userCount);
            assertThat(ranks).allMatch(rank -> rank >= 0 && rank < userCount);
        }
    }

    @Nested
    @DisplayName("입장 토큰 — TTL 기반 만료")
    class EntryToken {

        @Test
        @DisplayName("발급한 토큰을 조회할 수 있고, 삭제하면 사라진다.")
        void issueFindDelete() {
            entryTokenRepository.issue(1L, "token-abc", Duration.ofMinutes(5));

            assertThat(entryTokenRepository.find(1L)).contains("token-abc");

            entryTokenRepository.delete(1L);

            assertThat(entryTokenRepository.find(1L)).isEmpty();
        }

        @Test
        @DisplayName("TTL 이 지나면 토큰은 자동 만료된다.")
        void tokenExpiresAfterTtl() throws InterruptedException {
            entryTokenRepository.issue(1L, "token-abc", Duration.ofMillis(500));

            assertThat(entryTokenRepository.find(1L)).contains("token-abc");

            Thread.sleep(700);

            assertThat(entryTokenRepository.find(1L)).isEmpty();
        }

        @Test
        @DisplayName("발급되지 않은 유저의 토큰은 empty 다.")
        void absentTokenIsEmpty() {
            assertThat(entryTokenRepository.find(99L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("in-flight 마크 — 동시 주문 1건 제한(SETNX)")
    class InFlight {

        @Test
        @DisplayName("선점은 처음 한 번만 성공하고, 해제 전까지 뒤따르는 선점은 실패한다.")
        void acquireIsExclusiveUntilReleased() {
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-a", Duration.ofSeconds(30))).isTrue();
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-b", Duration.ofSeconds(30))).isFalse();

            entryTokenRepository.releaseInFlight(1L, "owner-a");

            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-c", Duration.ofSeconds(30))).isTrue();
        }

        @Test
        @DisplayName("유저별로 독립적이다 — 다른 유저의 선점은 서로 막지 않는다.")
        void marksArePerUser() {
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-1", Duration.ofSeconds(30))).isTrue();

            assertThat(entryTokenRepository.acquireInFlight(2L, "owner-2", Duration.ofSeconds(30))).isTrue();
        }

        @Test
        @DisplayName("TTL 이 지나면 마크가 자동 해제된다 — 크래시로 해제가 누락돼도 영구 잠금이 없다.")
        void markExpiresAfterTtl() throws InterruptedException {
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-a", Duration.ofMillis(500))).isTrue();
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-b", Duration.ofMillis(500))).isFalse();

            Thread.sleep(700);

            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-c", Duration.ofSeconds(30))).isTrue();
        }

        @Test
        @DisplayName("다른 owner token 의 해제는 무시된다 — 만료 후 새 소유자가 선점한 마크를 지우지 않는다.")
        void releaseOnlyByOwner() {
            entryTokenRepository.acquireInFlight(1L, "owner-a", Duration.ofSeconds(30));

            // 소유자가 아닌 토큰으로 해제 시도 → 무시(compare-and-delete)
            entryTokenRepository.releaseInFlight(1L, "owner-b");
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-c", Duration.ofSeconds(30))).isFalse(); // 여전히 잠김

            // 진짜 소유자로 해제하면 풀린다
            entryTokenRepository.releaseInFlight(1L, "owner-a");
            assertThat(entryTokenRepository.acquireInFlight(1L, "owner-c", Duration.ofSeconds(30))).isTrue();
        }
    }
}
