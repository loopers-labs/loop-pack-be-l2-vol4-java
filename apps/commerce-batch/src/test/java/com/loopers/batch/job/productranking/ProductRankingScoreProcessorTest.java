package com.loopers.batch.job.productranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRankingScoreProcessorTest {

  private final ProductRankingScoreProcessor processor = new ProductRankingScoreProcessor();

  @DisplayName("조회수, 좋아요수, 주문건수 가중치를 BigDecimal로 계산한다.")
  @Test
  void calculatesWeightedScore() {
    ProductMetricItem metric = new ProductMetricItem(LocalDate.of(2026, 7, 22), 10L, 13L, 7L, 3L);

    ProductRankingScore result = processor.process(metric);

    assertThat(result.productId()).isEqualTo(10L);
    assertThat(result.score()).isEqualByComparingTo(new BigDecimal("5.7"));
  }
}
