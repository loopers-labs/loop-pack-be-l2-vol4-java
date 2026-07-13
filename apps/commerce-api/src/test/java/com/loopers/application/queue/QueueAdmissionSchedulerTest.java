package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRedisStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QueueAdmissionScheduler 단위 테스트.
 *
 * <p>재고 판정을 하지 않으므로(순서 보장만 담당) StockRepository 의존이 없다 — admitBatch 위임과
 * 발급된 유저에 대한 issueToken 호출만 검증한다.
 */
class QueueAdmissionSchedulerTest {

    private static final Long PRODUCT_ID = 1L;

    private QueueRedisStore queueRedisStore;
    private QueueAdmissionScheduler sut;

    @BeforeEach
    void setUp() {
        queueRedisStore = mock(QueueRedisStore.class);
    }

    private QueueProperties propertiesWith(int batchSize, long tokenTtlSeconds) {
        return new QueueProperties(
            List.of(PRODUCT_ID),
            batchSize,
            tokenTtlSeconds,
            1000L,
            2000L,
            5000L
        );
    }

    @DisplayName("게이트 대상 상품마다 설정된 배치 크기로 admitBatch를 호출한다.")
    @Test
    void admit_callsAdmitBatch_withConfiguredBatchSize() {
        // arrange
        QueueProperties properties = propertiesWith(20, 300);
        sut = new QueueAdmissionScheduler(queueRedisStore, properties);
        when(queueRedisStore.admitBatch(eq(PRODUCT_ID), eq(20))).thenReturn(List.of());

        // act
        sut.admit();

        // assert
        verify(queueRedisStore).admitBatch(PRODUCT_ID, 20);
    }

    @DisplayName("admitBatch가 유저 목록을 반환하면, 각 유저마다 정확히 한 번씩 설정된 TTL로 issueToken이 호출된다.")
    @Test
    void issueToken_isCalledOncePerAdmittedUser_withConfiguredTtl() {
        // arrange
        long ttlSeconds = 300L;
        QueueProperties properties = propertiesWith(20, ttlSeconds);
        sut = new QueueAdmissionScheduler(queueRedisStore, properties);
        List<Long> admittedUserIds = List.of(11L, 22L, 33L);
        when(queueRedisStore.admitBatch(eq(PRODUCT_ID), eq(20))).thenReturn(admittedUserIds);

        // act
        sut.admit();

        // assert — 각 유저마다 정확히 1회, TTL은 설정값 그대로
        Duration expectedTtl = Duration.ofSeconds(ttlSeconds);
        for (Long userId : admittedUserIds) {
            verify(queueRedisStore, times(1)).issueToken(PRODUCT_ID, userId, expectedTtl);
        }
        verify(queueRedisStore, times(admittedUserIds.size()))
            .issueToken(eq(PRODUCT_ID), any(), eq(expectedTtl));
    }

    @DisplayName("admitBatch가 빈 목록을 반환하면, issueToken은 전혀 호출되지 않는다.")
    @Test
    void issueToken_isNeverCalled_whenAdmitBatchReturnsEmpty() {
        // arrange
        QueueProperties properties = propertiesWith(20, 300);
        sut = new QueueAdmissionScheduler(queueRedisStore, properties);
        when(queueRedisStore.admitBatch(eq(PRODUCT_ID), eq(20))).thenReturn(List.of());

        // act
        sut.admit();

        // assert
        verify(queueRedisStore, never()).issueToken(eq(PRODUCT_ID), any(), any());
    }

    @DisplayName("한 상품 처리 중 예외가 나도, 다른 게이트 대상 상품 처리는 계속된다.")
    @Test
    void admit_continuesOtherProducts_whenOneProductThrows() {
        // arrange
        Long failingProductId = 1L;
        Long okProductId = 2L;
        QueueProperties properties = new QueueProperties(
            List.of(failingProductId, okProductId), 20, 300, 1000L, 2000L, 5000L
        );
        sut = new QueueAdmissionScheduler(queueRedisStore, properties);
        when(queueRedisStore.admitBatch(eq(failingProductId), eq(20)))
            .thenThrow(new IllegalStateException("redis down"));
        when(queueRedisStore.admitBatch(eq(okProductId), eq(20))).thenReturn(List.of(99L));

        // act
        sut.admit();

        // assert — failingProductId에서 예외가 나도 okProductId는 정상 처리된다.
        verify(queueRedisStore).issueToken(okProductId, 99L, Duration.ofSeconds(300));
    }
}
