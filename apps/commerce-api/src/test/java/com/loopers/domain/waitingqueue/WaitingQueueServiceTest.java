package com.loopers.domain.waitingqueue;

import com.loopers.config.waitingqueue.WaitingQueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WaitingQueueService 순수 단위 테스트 — Repository를 mock으로 격리해 Redis 없이
 * 멱등 진입·상태 판정·발급 배치(back-pressure)·토큰 검증/소모 규칙을 검증한다.
 */
class WaitingQueueServiceTest {

    private static final Long USER = 1001L;

    private WaitingQueueRepository queue;
    private EntryTokenRepository tokens;
    private TokenIssuer issuer;
    private WaitingQueueService service;

    @BeforeEach
    void setUp() {
        queue = mock(WaitingQueueRepository.class);
        tokens = mock(EntryTokenRepository.class);
        issuer = mock(TokenIssuer.class);
        // releaseSize(N)=30, interval(M)=2, throughput=15/s, tokenTtl=30, hardMax=500
        ThroughputPolicy policy = new ThroughputPolicy(
            new WaitingQueueProperties(true, 30, 2, 30, 2, 500));
        service = new WaitingQueueService(queue, tokens, policy, issuer);
    }

    @Nested
    @DisplayName("enter — 대기열 진입")
    class Enter {
        @Test
        @DisplayName("이미 활성(토큰 보유)이면 READY, 큐에 넣지 않는다")
        void alreadyActive() {
            when(tokens.isActive(USER)).thenReturn(true);

            QueueSnapshot snapshot = service.enter(USER);

            assertThat(snapshot.status()).isEqualTo(QueueStatus.READY);
            verify(queue, never()).enqueueIfAbsent(USER);
        }

        @Test
        @DisplayName("신규 진입 시 enqueue 후 WAITING + 순번/ETA 반환")
        void newEntry() {
            when(tokens.isActive(USER)).thenReturn(false);
            when(queue.rank(USER)).thenReturn(59L); // 0-based → 앞선 60명

            QueueSnapshot snapshot = service.enter(USER);

            verify(queue).enqueueIfAbsent(USER);
            assertThat(snapshot.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(snapshot.rank()).isEqualTo(60L);        // 1-based
            assertThat(snapshot.aheadCount()).isEqualTo(59L);
            assertThat(snapshot.estimatedWaitSeconds()).isEqualTo(4L); // ceil(59/15)
        }

        @Test
        @DisplayName("멱등: 이미 대기 중이어도 순번 유지(enqueueIfAbsent가 false 반환해도 rank 조회)")
        void idempotent() {
            when(tokens.isActive(USER)).thenReturn(false);
            when(queue.enqueueIfAbsent(USER)).thenReturn(false);
            when(queue.rank(USER)).thenReturn(4L);

            QueueSnapshot snapshot = service.enter(USER);

            assertThat(snapshot.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(snapshot.rank()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("resolve — 순번 조회")
    class Resolve {
        @Test
        @DisplayName("활성이면 READY")
        void ready() {
            when(tokens.isActive(USER)).thenReturn(true);
            assertThat(service.resolve(USER).status()).isEqualTo(QueueStatus.READY);
        }

        @Test
        @DisplayName("큐에 없으면 NOT_IN_QUEUE")
        void notInQueue() {
            when(tokens.isActive(USER)).thenReturn(false);
            when(queue.rank(USER)).thenReturn(null);
            assertThat(service.resolve(USER).status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }
    }

    @Nested
    @DisplayName("issueBatch — 방류 원자 위임")
    class IssueBatch {
        @Test
        @DisplayName("N(=releaseSize)개의 후보 토큰을 만들어 TokenIssuer에 원자 방류를 위임한다")
        @SuppressWarnings("unchecked")
        void delegatesToIssuer() {
            // issueFront(releaseSize=30, interval=2, ttl=30, hardMax=500, tokens)
            when(issuer.issueFront(eq(30), eq(2), eq(30), eq(500), anyList()))
                .thenReturn(List.of(1L, 2L, 3L)); // 3명 방류됨

            int issued = service.issueBatch();

            assertThat(issued).isEqualTo(3);
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(issuer).issueFront(eq(30), eq(2), eq(30), eq(500), captor.capture());
            // 후보 토큰 = 방류 최대치(N), 중복·null 없음
            assertThat(captor.getValue()).hasSize(30).doesNotContainNull();
            assertThat(captor.getValue()).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("방류 0이면 0을 반환한다(대기열 비었거나 이번 윈도우 소진)")
        void none() {
            when(issuer.issueFront(anyInt(), anyInt(), anyInt(), anyInt(), anyList())).thenReturn(List.of());

            assertThat(service.issueBatch()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("validateToken — 주문 진입 가드")
    class Validate {
        @Test
        @DisplayName("토큰 없으면 FORBIDDEN")
        void missing() {
            Throwable t = catchThrowable(() -> service.validateToken(USER, null));
            assertThat(t).isInstanceOf(CoreException.class);
            assertThat(((CoreException) t).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @Test
        @DisplayName("존재하지 않는/만료 토큰이면 FORBIDDEN")
        void expired() {
            when(tokens.findUserIdByToken("tk")).thenReturn(null);
            Throwable t = catchThrowable(() -> service.validateToken(USER, "tk"));
            assertThat(((CoreException) t).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @Test
        @DisplayName("소유자 불일치면 FORBIDDEN")
        void mismatch() {
            when(tokens.findUserIdByToken("tk")).thenReturn(2002L);
            Throwable t = catchThrowable(() -> service.validateToken(USER, "tk"));
            assertThat(((CoreException) t).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @Test
        @DisplayName("유효하면 통과(예외 없음)")
        void valid() {
            when(tokens.findUserIdByToken("tk")).thenReturn(USER);
            Throwable t = catchThrowable(() -> service.validateToken(USER, "tk"));
            assertThat(t).isNull();
        }
    }

    @Test
    @DisplayName("consume — 토큰/활성 슬롯 회수 위임")
    void consume() {
        service.consume(USER, "tk");
        verify(tokens).consume(USER, "tk");
    }
}
