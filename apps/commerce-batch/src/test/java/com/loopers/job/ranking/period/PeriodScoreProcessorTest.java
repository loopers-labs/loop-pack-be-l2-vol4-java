package com.loopers.job.ranking.period;

import com.loopers.batch.job.ranking.period.PeriodMetricsSum;
import com.loopers.batch.job.ranking.period.PeriodScore;
import com.loopers.batch.job.ranking.period.PeriodScorePolicy;
import com.loopers.batch.job.ranking.period.step.PeriodScoreProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 구간 스코어 산식 + 필터링 검증.
 *
 * <p>가중치는 commerce-streamer 의 {@code RankingScorePolicy} 와 같아야 한다(조회 0.1 / 좋아요 0.2).
 * 주문은 {@code order_score} 에 이미 0.6×log10 이 적용돼 있으므로 여기서 다시 곱하지 않는다.
 */
class PeriodScoreProcessorTest {

    private final PeriodScoreProcessor processor = new PeriodScoreProcessor();

    @DisplayName("조회·좋아요에 가중치를 적용하고 주문스코어는 그대로 더한다.")
    @Test
    void appliesWeights() {
        // 조회 100 → 10.0 / 좋아요 5 → 1.0 / 주문스코어 2.5 그대로
        PeriodScore result = processor.process(new PeriodMetricsSum(10L, 100L, 5L, 2.5));

        assertThat(result).isNotNull();
        assertThat(result.productId()).isEqualTo(10L);
        assertThat(result.score()).isCloseTo(13.5, within(1e-9));
    }

    @DisplayName("주문 가중치를 이중으로 곱하지 않는다 — order_score 는 이미 0.6이 반영된 값이다.")
    @Test
    void doesNotReapplyOrderWeight() {
        PeriodScore result = processor.process(new PeriodMetricsSum(10L, 0L, 0L, 3.0));

        assertThat(result.score()).isCloseTo(3.0, within(1e-9));
    }

    @DisplayName("좋아요가 음수여도 합산에 그대로 반영된다(취소가 많았던 구간).")
    @Test
    void allowsNegativeLikeSum() {
        // 조회 100 → 10.0, 좋아요 -10 → -2.0 ⇒ 8.0
        PeriodScore result = processor.process(new PeriodMetricsSum(10L, 100L, -10L, 0.0));

        assertThat(result.score()).isCloseTo(8.0, within(1e-9));
    }

    @DisplayName("스코어가 0 이하인 상품은 걸러낸다(null 반환 → Writer 로 넘어가지 않음).")
    @Test
    void filtersNonPositiveScore() {
        // 좋아요만 -1 ⇒ -0.2
        assertThat(processor.process(new PeriodMetricsSum(10L, 0L, -1L, 0.0))).isNull();
        // 활동이 상쇄되어 정확히 0
        assertThat(processor.process(new PeriodMetricsSum(10L, 0L, 0L, 0.0))).isNull();
    }

    @DisplayName("가중치 상수가 streamer 정책과 일치한다.")
    @Test
    void weightsMatchRealtimePolicy() {
        assertThat(PeriodScorePolicy.VIEW_WEIGHT).isEqualTo(0.1);
        assertThat(PeriodScorePolicy.LIKE_WEIGHT).isEqualTo(0.2);
    }
}
