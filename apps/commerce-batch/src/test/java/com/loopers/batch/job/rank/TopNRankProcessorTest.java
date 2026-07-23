package com.loopers.batch.job.rank;

import com.loopers.domain.rank.WeeklyProductRankModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TopNRankProcessorTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 15);
    private static final LocalDate END = LocalDate.of(2026, 7, 21);

    private TopNRankProcessor<WeeklyProductRankModel> processor(int topN) {
        return new TopNRankProcessor<>(topN, START, END, WeeklyProductRankModel::new);
    }

    private ProductMetricsAggregate agg(long productId, long views, long likes, long sales) {
        return new ProductMetricsAggregate(productId, views, likes, sales);
    }

    @DisplayName("읽은 순서대로 1-based 순위를 부여하고, 점수·기간을 채운 엔티티를 만든다.")
    @Test
    void assignsSequentialRank_withScoreAndPeriod() {
        // arrange
        TopNRankProcessor<WeeklyProductRankModel> processor = processor(100);

        // act
        WeeklyProductRankModel first = processor.process(agg(10L, 0, 0, 10)); // score 6.0
        WeeklyProductRankModel second = processor.process(agg(20L, 50, 0, 0)); // score 5.0

        // assert
        assertThat(first.getRankNo()).isEqualTo(1);
        assertThat(first.getProductId()).isEqualTo(10L);
        assertThat(first.getScore()).isEqualTo(6.0);
        assertThat(first.getPeriodStart()).isEqualTo(START);
        assertThat(first.getPeriodEnd()).isEqualTo(END);
        assertThat(second.getRankNo()).isEqualTo(2);
        assertThat(second.getProductId()).isEqualTo(20L);
        assertThat(second.getScore()).isEqualTo(5.0);
    }

    @DisplayName("TOP N을 초과하는 순위의 항목은 null을 반환해 청크에서 제외된다.")
    @Test
    void returnsNull_beyondTopN() {
        // arrange — TOP 2
        TopNRankProcessor<WeeklyProductRankModel> processor = processor(2);

        // act
        WeeklyProductRankModel rank1 = processor.process(agg(1L, 0, 0, 3));
        WeeklyProductRankModel rank2 = processor.process(agg(2L, 0, 0, 2));
        WeeklyProductRankModel rank3 = processor.process(agg(3L, 0, 0, 1)); // 3번째 → 컷

        // assert
        assertThat(rank1.getRankNo()).isEqualTo(1);
        assertThat(rank2.getRankNo()).isEqualTo(2);
        assertThat(rank3).isNull();
    }

    @DisplayName("컷 이후에도 카운터는 계속 증가하므로, 더 뒤 항목도 계속 null이다.")
    @Test
    void keepsFiltering_afterCutoff() {
        // arrange — TOP 1
        TopNRankProcessor<WeeklyProductRankModel> processor = processor(1);

        // act
        WeeklyProductRankModel rank1 = processor.process(agg(1L, 0, 0, 5));
        WeeklyProductRankModel rank2 = processor.process(agg(2L, 0, 0, 4));
        WeeklyProductRankModel rank3 = processor.process(agg(3L, 0, 0, 3));

        // assert
        assertThat(rank1.getRankNo()).isEqualTo(1);
        assertThat(rank2).isNull();
        assertThat(rank3).isNull();
    }
}
