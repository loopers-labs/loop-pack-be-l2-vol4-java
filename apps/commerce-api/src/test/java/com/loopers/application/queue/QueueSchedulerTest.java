package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenDlqRepository;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 로직 단위 테스트. Redis 없이 in-memory fake로 결정론적으로 검증한다.
 * (스케줄 대기 없이 tick()을 직접 호출한다.)
 */
class QueueSchedulerTest {

    private FakeWaitingQueueRepository queueRepo;
    private FakeEntryTokenRepository tokenRepo;
    private FakeDlqRepository dlqRepo;
    private WaitingQueueService waitingQueueService;
    private EntryTokenService entryTokenService;
    private QueueScheduler scheduler;

    @BeforeEach
    void setUp() {
        queueRepo = new FakeWaitingQueueRepository();
        tokenRepo = new FakeEntryTokenRepository();
        dlqRepo = new FakeDlqRepository();
        waitingQueueService = new WaitingQueueService(queueRepo);
        entryTokenService = new EntryTokenService(tokenRepo, Duration.ofMinutes(5));
        scheduler = new QueueScheduler(waitingQueueService, entryTokenService, dlqRepo, 14);
    }

    @DisplayName("tick 1회 호출 시, 대기열 앞 N명이 큐에서 빠지고 각자 유효한 토큰을 보유한다.")
    @Test
    void admitsFrontUsers_andIssuesTokens_onTick() {
        // arrange: 3명 진입 (배치 크기 14 ≥ 3 이므로 전원 입장)
        waitingQueueService.enter(1L);
        waitingQueueService.enter(2L);
        waitingQueueService.enter(3L);

        // act
        scheduler.tick();

        // assert
        assertThat(queueRepo.size()).isZero();
        assertThat(entryTokenService.validate(1L, tokenRepo.tokenOf(1L))).isTrue();
        assertThat(entryTokenService.validate(2L, tokenRepo.tokenOf(2L))).isTrue();
        assertThat(entryTokenService.validate(3L, tokenRepo.tokenOf(3L))).isTrue();
    }

    @DisplayName("토큰 발급이 실패한 유저는 메인 큐가 아니라 DLQ로 들어간다(순번 역전 없음).")
    @Test
    void routesFailedIssueToDlq_notBackToQueue() {
        // arrange: 2번 유저 발급만 실패하도록 설정
        tokenRepo.failFor(2L);
        waitingQueueService.enter(1L);
        waitingQueueService.enter(2L);
        waitingQueueService.enter(3L);

        // act
        scheduler.tick();

        // assert: 1·3은 토큰 보유, 2는 DLQ에 있고 메인 큐는 비어 있음
        assertThat(entryTokenService.validate(1L, tokenRepo.tokenOf(1L))).isTrue();
        assertThat(entryTokenService.validate(3L, tokenRepo.tokenOf(3L))).isTrue();
        assertThat(queueRepo.size()).isZero();
        assertThat(dlqRepo.snapshot()).containsExactly(2L);
    }

    @DisplayName("DLQ에 쌓인 실패분은 다음 tick의 재시도에서 발급에 성공하면 비워진다.")
    @Test
    void retriesDlq_andClearsOnSuccess() {
        // arrange: 첫 tick에서 2번이 실패해 DLQ로 감
        tokenRepo.failFor(2L);
        waitingQueueService.enter(1L);
        waitingQueueService.enter(2L);
        scheduler.tick();
        assertThat(dlqRepo.snapshot()).containsExactly(2L);

        // act: 장애 해소 후 다시 tick → 재시도로 발급 성공
        tokenRepo.recover();
        scheduler.tick();

        // assert
        assertThat(dlqRepo.snapshot()).isEmpty();
        assertThat(entryTokenService.validate(2L, tokenRepo.tokenOf(2L))).isTrue();
    }

    // ---- in-memory fakes ----

    /**
     * ZPOPMIN 흉내. Redis ZSet처럼 score가 같아도 member를 각각 보존하고,
     * 동점은 진입(삽입) 순서로 정렬한다. (LinkedHashMap + 안정 정렬)
     */
    static class FakeWaitingQueueRepository implements WaitingQueueRepository {
        private final LinkedHashMap<Long, Double> scoreOf = new LinkedHashMap<>();

        @Override
        public boolean add(Long userId, double score) {
            if (scoreOf.containsKey(userId)) {
                return false;
            }
            scoreOf.put(userId, score);
            return true;
        }

        private List<Long> ordered() {
            // sorted()는 안정 정렬 → score 동점이면 LinkedHashMap의 삽입 순서를 유지한다.
            return scoreOf.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
        }

        @Override
        public Optional<Long> rank(Long userId) {
            int index = ordered().indexOf(userId);
            return index < 0 ? Optional.empty() : Optional.of((long) index);
        }

        @Override
        public long size() {
            return scoreOf.size();
        }

        @Override
        public List<Long> pollFront(long count) {
            List<Long> ordered = ordered();
            List<Long> out = new ArrayList<>();
            for (int i = 0; i < count && i < ordered.size(); i++) {
                out.add(ordered.get(i));
            }
            out.forEach(scoreOf::remove);
            return out;
        }
    }

    /** in-memory 토큰 저장 + 특정 userId 발급 실패 주입. */
    static class FakeEntryTokenRepository implements EntryTokenRepository {
        private final Map<Long, String> store = new HashMap<>();
        private final Set<Long> failing = new HashSet<>();

        void failFor(Long userId) {
            failing.add(userId);
        }

        void recover() {
            failing.clear();
        }

        String tokenOf(Long userId) {
            return store.get(userId);
        }

        @Override
        public void save(Long userId, String token, Duration ttl) {
            if (failing.contains(userId)) {
                throw new IllegalStateException("발급 실패 주입: " + userId);
            }
            store.put(userId, token);
        }

        @Override
        public Optional<String> find(Long userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public void delete(Long userId) {
            store.remove(userId);
        }
    }

    /** FIFO 실패함. */
    static class FakeDlqRepository implements EntryTokenDlqRepository {
        private final Deque<Long> queue = new ArrayDeque<>();

        List<Long> snapshot() {
            return new ArrayList<>(queue);
        }

        @Override
        public void push(Long userId) {
            queue.addLast(userId);
        }

        @Override
        public List<Long> drain(long count) {
            List<Long> out = new ArrayList<>();
            for (long i = 0; i < count && !queue.isEmpty(); i++) {
                out.add(queue.pollFirst());
            }
            return out;
        }
    }
}
