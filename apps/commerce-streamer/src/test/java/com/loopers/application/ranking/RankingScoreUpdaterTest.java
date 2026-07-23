package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RankingScoreUpdaterTest {

    @Mock private RankingRepository rankingRepository;

    private static final Long PRODUCT_ID = 10L;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private RankingScoreUpdater updater() {
        return new RankingScoreUpdater(rankingRepository);
    }

    @DisplayName("가중치 계산 — 트랜잭션이 없으면 즉시 반영된다.")
    @Nested
    class Weight {

        @DisplayName("조회 이벤트는 0.1점이 가산된다.")
        @Test
        void accumulatesViewScore() {
            // act
            updater().onProductViewed(PRODUCT_ID);

            // assert
            then(rankingRepository).should()
                .incrementScore(eq(LocalDate.now()), eq(PRODUCT_ID), eq(0.1));
        }

        @DisplayName("좋아요 이벤트는 0.2점이 가산된다.")
        @Test
        void accumulatesLikeScore() {
            // act
            updater().onProductLiked(PRODUCT_ID);

            // assert
            then(rankingRepository).should()
                .incrementScore(eq(LocalDate.now()), eq(PRODUCT_ID), eq(0.2));
        }

        @DisplayName("좋아요 취소 이벤트는 0.2점이 감점된다.")
        @Test
        void accumulatesUnlikeScore() {
            // act
            updater().onProductUnliked(PRODUCT_ID);

            // assert
            then(rankingRepository).should()
                .incrementScore(eq(LocalDate.now()), eq(PRODUCT_ID), eq(-0.2));
        }

        @DisplayName("주문 이벤트는 0.6 × 단가 × 수량 점수가 가산된다.")
        @Test
        void accumulatesOrderScore_byPriceAndQuantity() {
            // act
            updater().onOrderPaid(PRODUCT_ID, 5_000, 2);

            // assert — 0.6 × 5000 × 2 = 6000
            then(rankingRepository).should()
                .incrementScore(eq(LocalDate.now()), eq(PRODUCT_ID), eq(6_000.0));
        }

        @DisplayName("단가가 0인 주문(price 필드가 없던 구버전 이벤트)은 랭킹에 반영하지 않는다.")
        @Test
        void skipsAccumulation_whenOrderScoreIsZero() {
            // act
            updater().onOrderPaid(PRODUCT_ID, 0, 2);

            // assert
            then(rankingRepository).should(never()).incrementScore(any(), anyLong(), anyDouble());
        }
    }

    @DisplayName("트랜잭션 동기화가 활성화된 경우,")
    @Nested
    class AfterCommit {

        @DisplayName("커밋 전에는 반영하지 않고, 커밋 후에 반영한다.")
        @Test
        void accumulatesOnlyAfterCommit() {
            // arrange
            TransactionSynchronizationManager.initSynchronization();

            // act — 등록만 하고 아직 커밋 전
            updater().onProductViewed(PRODUCT_ID);

            // assert — 커밋 전 미반영
            then(rankingRepository).should(never()).incrementScore(any(), anyLong(), anyDouble());

            // act — 커밋 완료 시점 재현
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

            // assert — 커밋 후 반영
            then(rankingRepository).should()
                .incrementScore(eq(LocalDate.now()), eq(PRODUCT_ID), eq(0.1));
        }
    }

    @DisplayName("Redis 반영이 실패해도,")
    @Nested
    class RedisFailure {

        @DisplayName("예외를 전파하지 않는다 — 점수 유실을 허용하고 메시지 처리는 성공으로 간주한다.")
        @Test
        void swallowsException_whenRedisFails() {
            // arrange
            willThrow(new RuntimeException("redis down"))
                .given(rankingRepository).incrementScore(any(), anyLong(), anyDouble());

            // act & assert
            assertThatCode(() -> updater().onProductViewed(PRODUCT_ID)).doesNotThrowAnyException();
        }
    }
}
