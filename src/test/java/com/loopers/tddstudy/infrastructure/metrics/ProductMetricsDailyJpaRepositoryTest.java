package com.loopers.tddstudy.infrastructure.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import com.loopers.tddstudy.domain.ranking.RankingPolicy;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@DataJpaTest
class ProductMetricsDailyJpaRepositoryTest {

    @Autowired
    ProductMetricsDailyJpaRepository repository;

    @Test
    void 특정_날짜의_행들만_삭제한다() {
        // given: 7/21에 2건, 7/22에 1건 저장
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 21), 1, 0, 0));
        repository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 7, 21), 2, 0, 0));
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 22), 3, 0, 0));

        // when: 7/21만 삭제
        repository.deleteByMetricDate(LocalDate.of(2026, 7, 21));

        // then: 7/22 것 1건만 남음
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 기간내_상품별로_합산하고_점수순으로_정렬한다() {
        // 상품1: 7/1 like10, 7/2 sales10 → like합10, sales합10
        //        점수 = 10*0.2 + 10*0.7 = 9.0
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 1), 10, 0, 0));
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 2), 0, 10, 0));
        // 상품2: 7/1 sales5 → 점수 = 5*0.7 = 3.5
        repository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 7, 1), 0, 5, 0));

        List<ProductScoreSum> result = repository.aggregateTopScores(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7),
                RankingPolicy.likeWeight(), RankingPolicy.salesWeight(),
                PageRequest.of(0, 100));

        assertThat(result).hasSize(2);
        // 점수 높은 상품1이 먼저
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).likeSum()).isEqualTo(10);
        assertThat(result.get(0).salesSum()).isEqualTo(10);
        assertThat(result.get(0).score()).isEqualTo(9.0);
        assertThat(result.get(1).productId()).isEqualTo(2L);
        assertThat(result.get(1).score()).isEqualTo(3.5);
    }

    @Test
    void 기간_밖의_데이터는_집계에서_빠진다() {
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 1), 10, 0, 0));
        repository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 7, 10), 99, 0, 0));  // 범위 밖

        List<ProductScoreSum> result = repository.aggregateTopScores(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7),
                RankingPolicy.likeWeight(), RankingPolicy.salesWeight(),
                PageRequest.of(0, 100));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
    }

    @Test
    void 요청한_개수만큼만_가져온다() {
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 1), 10, 0, 0));
        repository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 7, 1), 5, 0, 0));
        repository.save(new ProductMetricsDaily(3L, LocalDate.of(2026, 7, 1), 1, 0, 0));

        List<ProductScoreSum> top1 = repository.aggregateTopScores(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7),
                RankingPolicy.likeWeight(), RankingPolicy.salesWeight(),
                PageRequest.of(0, 1));       // 1개만!

        assertThat(top1).hasSize(1);
        assertThat(top1.get(0).productId()).isEqualTo(1L);   // 가장 높은 것
    }

    @Test
    void 음수_델타도_점수에_그대로_반영된다() {
        // 설계에서 정한 규칙: 좋아요 취소는 점수를 깎는다
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 1), 10, 0, 0));
        repository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 2), -10, 0, 0));

        List<ProductScoreSum> result = repository.aggregateTopScores(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7),
                RankingPolicy.likeWeight(), RankingPolicy.salesWeight(),
                PageRequest.of(0, 100));

        assertThat(result.get(0).likeSum()).isEqualTo(0);   // 10 + (-10)
        assertThat(result.get(0).score()).isEqualTo(0.0);
    }

}
