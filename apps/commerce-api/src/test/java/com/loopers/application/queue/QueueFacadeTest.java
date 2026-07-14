package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueFacadeTest {

    private static final long INTERVAL_MS = 100;
    private static final int BATCH_SIZE = 14; // 100ms × 14명 = 초당 140명 배출 (풀 40 기준 안전 140 TPS 산정과 동일)
    private static final long TOKEN_TTL_SECONDS = 300;

    private final FakeWaitingQueueRepository waitingQueue = new FakeWaitingQueueRepository();
    private final FakeEntryTokenRepository entryTokens = new FakeEntryTokenRepository();
    // 대기열 운영 중(게이트 on) 파사드 — off 시 거부는 GateOff 에서 별도 검증
    private final QueueFacade queueFacade = new QueueFacade(waitingQueue, entryTokens, properties(true));

    private static QueueProperties properties(boolean gateEnabled) {
        return new QueueProperties(
            new QueueProperties.Admission(INTERVAL_MS, BATCH_SIZE),
            new QueueProperties.Token(TOKEN_TTL_SECONDS),
            new QueueProperties.OrderGate(gateEnabled)
        );
    }

    /** 대기열을 rank 가 결정되도록 score 오름차순으로 미리 채운다. (id 는 사전순 = 진입순 이 되게 0-패딩) */
    private void seedWaiting(int count) {
        for (int i = 1; i <= count; i++) {
            waitingQueue.enqueue(loginId(i), i);
        }
    }

    private String loginId(int i) {
        return String.format("user%04d", i);
    }

    @DisplayName("대기열에 진입할 때, ")
    @Nested
    class Enter {

        @DisplayName("신규 유저면 줄 끝에 서고, 순번과 전체 대기 인원을 반환한다.")
        @Test
        void entersQueue_whenNewUser() {
            // arrange
            seedWaiting(2);

            // act
            QueueInfo info = queueFacade.enter("newbie");

            // assert
            assertAll(
                () -> assertThat(info.position()).isEqualTo(3),
                () -> assertThat(info.waitingCount()).isEqualTo(3),
                () -> assertThat(info.token()).isNull()
            );
        }

        @DisplayName("이미 대기 중인 유저가 재진입하면, 줄 뒤로 밀리지 않고 기존 순번을 그대로 반환한다. (멱등)")
        @Test
        void keepsPosition_whenReenter() {
            // arrange
            QueueInfo first = queueFacade.enter("userA");
            queueFacade.enter("userB");

            // act
            QueueInfo reentered = queueFacade.enter("userA");

            // assert
            assertAll(
                () -> assertThat(first.position()).isEqualTo(1),
                () -> assertThat(reentered.position()).isEqualTo(1),
                () -> assertThat(reentered.waitingCount()).isEqualTo(2)
            );
        }

        @DisplayName("이미 입장 토큰을 보유한 유저면, 대기열에 다시 세우지 않고 position 0 과 토큰을 반환한다.")
        @Test
        void returnsToken_whenAlreadyAdmitted() {
            // arrange
            entryTokens.issue("vip", "token-vip", Duration.ofSeconds(TOKEN_TTL_SECONDS));

            // act
            QueueInfo info = queueFacade.enter("vip");

            // assert
            assertAll(
                () -> assertThat(info.position()).isZero(),
                () -> assertThat(info.token()).isEqualTo("token-vip"),
                () -> assertThat(info.estimatedWaitSeconds()).isZero(),
                () -> assertThat(waitingQueue.countWaiting()).isZero()
            );
        }
    }

    @DisplayName("순번을 조회할 때, ")
    @Nested
    class GetPosition {

        @DisplayName("대기 중이면 순번(rank+1)·전체 대기 인원·예상 대기 시간을 반환한다.")
        @Test
        void returnsPosition_whenWaiting() {
            // arrange
            seedWaiting(5);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(3));

            // assert
            assertAll(
                () -> assertThat(info.position()).isEqualTo(3),
                () -> assertThat(info.waitingCount()).isEqualTo(5),
                () -> assertThat(info.estimatedWaitSeconds()).isEqualTo(1),
                () -> assertThat(info.token()).isNull()
            );
        }

        @DisplayName("입장 토큰을 보유하면 position 0·토큰·예상 대기 0초를 반환한다.")
        @Test
        void returnsToken_whenAdmitted() {
            // arrange
            entryTokens.issue("vip", "token-vip", Duration.ofSeconds(TOKEN_TTL_SECONDS));

            // act
            QueueInfo info = queueFacade.getPosition("vip");

            // assert
            assertAll(
                () -> assertThat(info.position()).isZero(),
                () -> assertThat(info.token()).isEqualTo("token-vip"),
                () -> assertThat(info.estimatedWaitSeconds()).isZero(),
                () -> assertThat(info.suggestedPollIntervalSeconds()).isZero()
            );
        }

        @DisplayName("대기 중도 아니고 토큰도 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNeverEntered() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> queueFacade.getPosition("ghost"));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("입장 배치를 실행할 때, ")
    @Nested
    class AdmitNextBatch {

        @DisplayName("앞에서부터 배치 크기만큼 토큰을 발급하고 대기열에서 제거하며, 나머지는 순번이 당겨진다.")
        @Test
        void admitsBatch_andShiftsRemaining() {
            // arrange
            seedWaiting(BATCH_SIZE + 2);

            // act
            queueFacade.admitNextBatch();

            // assert
            assertAll(
                () -> assertThat(waitingQueue.countWaiting()).isEqualTo(2),
                () -> assertThat(entryTokens.find(loginId(1))).isPresent(),
                () -> assertThat(entryTokens.find(loginId(BATCH_SIZE))).isPresent(),
                () -> assertThat(entryTokens.find(loginId(BATCH_SIZE + 1))).isEmpty(),
                () -> assertThat(queueFacade.getPosition(loginId(BATCH_SIZE + 1)).position()).isEqualTo(1)
            );
        }

        @DisplayName("대기 인원이 배치 크기보다 적으면, 있는 만큼만 입장시킨다.")
        @Test
        void admitsAll_whenLessThanBatchSize() {
            // arrange
            seedWaiting(3);

            // act
            queueFacade.admitNextBatch();

            // assert
            assertAll(
                () -> assertThat(waitingQueue.countWaiting()).isZero(),
                () -> assertThat(entryTokens.find(loginId(1))).isPresent(),
                () -> assertThat(entryTokens.find(loginId(2))).isPresent(),
                () -> assertThat(entryTokens.find(loginId(3))).isPresent()
            );
        }

        @DisplayName("입장한 유저는 토큰을 얻어 position 0 이 된다.")
        @Test
        void admittedUserGetsToken() {
            // arrange
            seedWaiting(1);

            // act
            queueFacade.admitNextBatch();
            QueueInfo info = queueFacade.getPosition(loginId(1));

            // assert
            assertAll(
                () -> assertThat(info.position()).isZero(),
                () -> assertThat(info.token()).isNotBlank()
            );
        }

        @DisplayName("배치 중 한 명의 토큰 발급이 실패해도, 나머지는 발급되고 실패 유저는 큐에 남아 다음 tick 에 재시도된다.")
        @Test
        void isolatesIssueFailure_andKeepsFailedUserInQueue() {
            // arrange — 3명 대기, 2번째 유저의 발급만 실패하도록 주입
            seedWaiting(3);
            entryTokens.failIssueFor(loginId(2));

            // act
            queueFacade.admitNextBatch();

            // assert — 1·3번째는 발급+제거, 2번째는 토큰 없이 큐 맨 앞 잔류
            assertAll(
                () -> assertThat(entryTokens.find(loginId(1))).isPresent(),
                () -> assertThat(entryTokens.find(loginId(2))).isEmpty(),
                () -> assertThat(entryTokens.find(loginId(3))).isPresent(),
                () -> assertThat(waitingQueue.countWaiting()).isEqualTo(1),
                () -> assertThat(queueFacade.getPosition(loginId(2)).position()).isEqualTo(1)
            );
        }
    }

    @DisplayName("예상 대기 시간을 계산할 때, ")
    @Nested
    class EstimatedWaitSeconds {

        // 초당 배출량 = batchSize × (1000 / intervalMs) = 14 × 10 = 140명/초 (문서화된 산정과 동일 숫자)

        @DisplayName("순번 140번(1초 배출량 이내)이면 예상 대기 1초다.")
        @Test
        void oneSecond_whenWithinFirstSecondThroughput() {
            // arrange
            seedWaiting(140);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(140));

            // assert
            assertThat(info.estimatedWaitSeconds()).isEqualTo(1);
        }

        @DisplayName("순번 141번(1초 배출량 초과)이면 올림 처리되어 예상 대기 2초다.")
        @Test
        void twoSeconds_whenJustOverFirstSecondThroughput() {
            // arrange
            seedWaiting(141);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(141));

            // assert
            assertThat(info.estimatedWaitSeconds()).isEqualTo(2);
        }
    }

    @DisplayName("polling 주기를 안내할 때, (2차-1)")
    @Nested
    class SuggestedPollInterval {

        @DisplayName("순번 100번(빠른 구간 경계)까지는 1초를 안내한다.")
        @Test
        void oneSecond_atFastBoundary() {
            // arrange
            seedWaiting(100);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(100));

            // assert
            assertThat(info.suggestedPollIntervalSeconds()).isEqualTo(1);
        }

        @DisplayName("순번 101번(중간 구간 시작)부터는 3초를 안내한다.")
        @Test
        void threeSeconds_justOverFastBoundary() {
            // arrange
            seedWaiting(101);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(101));

            // assert
            assertThat(info.suggestedPollIntervalSeconds()).isEqualTo(3);
        }

        @DisplayName("순번 1000번(중간 구간 경계)까지는 3초를 안내한다.")
        @Test
        void threeSeconds_atMediumBoundary() {
            // arrange
            seedWaiting(1000);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(1000));

            // assert
            assertThat(info.suggestedPollIntervalSeconds()).isEqualTo(3);
        }

        @DisplayName("순번 1001번(느린 구간 시작)부터는 5초를 안내한다.")
        @Test
        void fiveSeconds_justOverMediumBoundary() {
            // arrange
            seedWaiting(1001);

            // act
            QueueInfo info = queueFacade.getPosition(loginId(1001));

            // assert
            assertThat(info.suggestedPollIntervalSeconds()).isEqualTo(5);
        }
    }

    @DisplayName("게이트가 꺼져 있으면(평시), ")
    @Nested
    class GateOff {

        // off 상태에서 진입을 받으면 입장 스케줄러가 없어 영원히 대기하는 함정 — API 자체를 거부한다
        private final QueueFacade closedFacade = new QueueFacade(waitingQueue, entryTokens, properties(false));

        @DisplayName("enter 는 BAD_REQUEST 로 거부하고 바로 주문 가능함을 안내한다.")
        @Test
        void rejectsEnter_whenGateOff() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> closedFacade.enter("tester01"));

            // assert
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(waitingQueue.countWaiting()).isZero()
            );
        }

        @DisplayName("getPosition 도 BAD_REQUEST 로 거부한다.")
        @Test
        void rejectsGetPosition_whenGateOff() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> closedFacade.getPosition("tester01"));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    /**
     * Redis ZSET 정렬 규칙(score 오름차순, 동점은 member 사전순)을 그대로 흉내낸 Fake.
     * enqueue 는 putIfAbsent 로 ZADD NX(재진입 시 score 유지)를, peek/remove 는 ZRANGE/ZREM 을 재현한다.
     */
    static class FakeWaitingQueueRepository implements WaitingQueueRepository {

        private final Map<String, Long> scores = new LinkedHashMap<>();

        @Override
        public boolean enqueue(String loginId, long enteredAtMillis) {
            return scores.putIfAbsent(loginId, enteredAtMillis) == null;
        }

        @Override
        public Optional<Long> findRank(String loginId) {
            List<String> ordered = ordered();
            int index = ordered.indexOf(loginId);
            return index >= 0 ? Optional.of((long) index) : Optional.empty();
        }

        @Override
        public long countWaiting() {
            return scores.size();
        }

        @Override
        public List<String> peekNextBatch(int count) {
            return ordered().stream().limit(Math.max(count, 0)).toList();
        }

        @Override
        public void remove(List<String> loginIds) {
            loginIds.forEach(scores::remove);
        }

        private List<String> ordered() {
            return scores.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
        }
    }

    /** TTL 은 무시하고 저장만 하는 인메모리 Fake — 발급 실패는 주입으로 재현한다(격리 검증용). */
    static class FakeEntryTokenRepository implements EntryTokenRepository {

        private final Map<String, String> tokens = new HashMap<>();
        private final Set<String> issueFailures = new HashSet<>();

        void failIssueFor(String loginId) {
            issueFailures.add(loginId);
        }

        @Override
        public void issue(String loginId, String token, Duration ttl) {
            if (issueFailures.contains(loginId)) {
                throw new IllegalStateException("토큰 발급 실패(테스트 주입): " + loginId);
            }
            tokens.put(loginId, token);
        }

        @Override
        public Optional<String> find(String loginId) {
            return Optional.ofNullable(tokens.get(loginId));
        }

        @Override
        public void delete(String loginId) {
            tokens.remove(loginId);
        }
    }
}
