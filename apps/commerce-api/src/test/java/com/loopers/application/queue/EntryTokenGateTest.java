package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntryTokenGateTest {

    private static final String LOGIN_ID = "tester01";
    private static final String TOKEN = "token-1";

    private final FakeEntryTokenRepository entryTokens = new FakeEntryTokenRepository();

    private static QueueProperties properties(boolean gateEnabled) {
        return new QueueProperties(
            new QueueProperties.Admission(100, 14),
            new QueueProperties.Token(300),
            new QueueProperties.OrderGate(gateEnabled)
        );
    }

    @DisplayName("게이트가 꺼져 있으면(기본값), ")
    @Nested
    class GateOff {

        private final EntryTokenGate gate = new EntryTokenGate(entryTokens, properties(false));

        @DisplayName("토큰이 없어도 검증을 통과한다. (기존 주문 흐름 불변)")
        @Test
        void passes_withoutToken() {
            // act & assert
            assertDoesNotThrow(() -> gate.verify(LOGIN_ID, null));
        }

        @DisplayName("consume 은 저장소를 건드리지 않는다.")
        @Test
        void consumeIsNoOp() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));

            // act
            gate.consume(LOGIN_ID);

            // assert
            assertThat(entryTokens.find(LOGIN_ID)).contains(TOKEN);
        }
    }

    @DisplayName("게이트가 켜져 있으면, ")
    @Nested
    class GateOn {

        private final EntryTokenGate gate = new EntryTokenGate(entryTokens, properties(true));

        @DisplayName("발급된 토큰과 헤더 값이 일치하면 통과한다.")
        @Test
        void passes_whenTokenMatches() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));

            // act & assert
            assertDoesNotThrow(() -> gate.verify(LOGIN_ID, TOKEN));
        }

        @DisplayName("발급된 토큰이 없으면(미진입·만료) BAD_REQUEST 로 거부하고 대기열 진입을 안내한다.")
        @Test
        void rejects_whenNoIssuedToken() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> gate.verify(LOGIN_ID, TOKEN));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("헤더 토큰이 누락되면 BAD_REQUEST 로 거부한다.")
        @Test
        void rejects_whenHeaderMissing() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));

            // act
            CoreException ex = assertThrows(CoreException.class, () -> gate.verify(LOGIN_ID, null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("헤더 토큰이 발급 값과 다르면 BAD_REQUEST 로 거부한다.")
        @Test
        void rejects_whenTokenMismatches() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));

            // act
            CoreException ex = assertThrows(CoreException.class, () -> gate.verify(LOGIN_ID, "forged-token"));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("consume 하면 1회용 토큰이 삭제되어 재사용할 수 없다.")
        @Test
        void consumeDeletesToken() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));

            // act
            gate.consume(LOGIN_ID);

            // assert
            assertThat(entryTokens.find(LOGIN_ID)).isEmpty();
            assertThrows(CoreException.class, () -> gate.verify(LOGIN_ID, TOKEN));
        }
    }

    @DisplayName("Redis 장애로 검증이 불가하면(Graceful Degradation), ")
    @Nested
    class RedisFailure {

        private final EntryTokenGate gate = new EntryTokenGate(entryTokens, properties(true));

        @DisplayName("조회가 1번 실패해도 즉시 재시도해 성공하면, 정상 검증 결과(통과)를 따른다.")
        @Test
        void retriesOnce_thenVerifiesNormally() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));
            entryTokens.failNextFinds(1);

            // act & assert — 재시도로 조회 성공 → 일치 토큰 통과
            assertDoesNotThrow(() -> gate.verify(LOGIN_ID, TOKEN));
            assertThat(entryTokens.findCalls()).isEqualTo(2);
        }

        @DisplayName("재시도로 조회에 성공했는데 토큰이 불일치하면, fail-open 이 아니라 정상 거부한다.")
        @Test
        void rejectsMismatch_evenAfterRetry() {
            // arrange
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));
            entryTokens.failNextFinds(1);

            // act
            CoreException ex = assertThrows(CoreException.class, () -> gate.verify(LOGIN_ID, "forged-token"));

            // assert — "검증 불가"(Redis 예외)와 "정상 거부"(불일치)는 구분된다
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재시도까지 연속 실패하면 fail-open 으로 통과시킨다. (가용성 우선 — 주문을 막지 않는다)")
        @Test
        void failsOpen_whenRetryAlsoFails() {
            // arrange — 원호출 + 재시도 모두 실패
            entryTokens.failNextFinds(2);

            // act & assert
            assertDoesNotThrow(() -> gate.verify(LOGIN_ID, null));
            assertThat(entryTokens.findCalls()).isEqualTo(2);
        }

        @DisplayName("토큰 소진(consume) 중엔 어떤 예외가 나도 무시한다. (best-effort — 완료된 주문을 되돌리지 않는다)")
        @Test
        void consumeIgnoresAnyFailure() {
            // arrange — DataAccessException 이 아닌 예외(셧다운 중 IllegalStateException 등)까지 삼켜야 한다
            entryTokens.issue(LOGIN_ID, TOKEN, Duration.ofMinutes(5));
            entryTokens.failDelete(true);

            // act & assert
            assertDoesNotThrow(() -> gate.consume(LOGIN_ID));
        }
    }

    /** TTL 은 무시하는 인메모리 Fake — 게이트 판정 로직만 검증하고, Redis 장애는 실패 주입으로 재현한다. */
    static class FakeEntryTokenRepository implements EntryTokenRepository {

        private final Map<String, String> tokens = new HashMap<>();
        private int findFailuresRemaining = 0;
        private boolean deleteFails = false;
        private int findCalls = 0;

        void failNextFinds(int count) {
            this.findFailuresRemaining = count;
        }

        void failDelete(boolean fails) {
            this.deleteFails = fails;
        }

        int findCalls() {
            return findCalls;
        }

        @Override
        public void issue(String loginId, String token, Duration ttl) {
            tokens.put(loginId, token);
        }

        @Override
        public Optional<String> find(String loginId) {
            findCalls++;
            if (findFailuresRemaining > 0) {
                findFailuresRemaining--;
                throw new RedisConnectionFailureException("Redis 연결 실패(테스트 주입)");
            }
            return Optional.ofNullable(tokens.get(loginId));
        }

        @Override
        public void delete(String loginId) {
            if (deleteFails) {
                // 비-DataAccessException — consume 의 catch(Exception) 계약(P0-5) 검증용
                throw new IllegalStateException("셧다운 중 커넥션 풀 종료(테스트 주입)");
            }
            tokens.remove(loginId);
        }
    }
}
