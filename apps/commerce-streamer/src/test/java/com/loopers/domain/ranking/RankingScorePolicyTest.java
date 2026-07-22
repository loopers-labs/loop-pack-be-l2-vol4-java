package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingScorePolicyTest {

  @DisplayName("완료 주문, 신규 좋아요, 조회 순으로 행동 1건의 점수가 높다.")
  @Test
  void scoresOrderHigherThanLikeAndView() {
    assertThat(RankingScorePolicy.score(ProductActivityEventType.PRODUCT_ORDERED)).isEqualTo(1.0D);
    assertThat(RankingScorePolicy.score(ProductActivityEventType.PRODUCT_LIKED)).isEqualTo(0.2D);
    assertThat(RankingScorePolicy.score(ProductActivityEventType.PRODUCT_VIEWED)).isEqualTo(0.1D);
    assertThat(RankingScorePolicy.PRODUCT_ORDERED_SCORE)
        .isGreaterThan(RankingScorePolicy.PRODUCT_LIKED_SCORE * 3);
  }
}
