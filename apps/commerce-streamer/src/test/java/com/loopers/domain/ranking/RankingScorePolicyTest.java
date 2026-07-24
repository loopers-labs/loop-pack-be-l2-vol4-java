package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScorePolicyTest {

    private final RankingScorePolicy policy = new RankingScorePolicy(0.1, 0.2, 0.6);

    @DisplayName("카탈로그 이벤트 타입별 delta — 조회 +0.1, 좋아요 +0.2, 좋아요취소 -0.2, 미지 타입 0.")
    @Test
    void catalogDelta() {
        assertThat(policy.catalogDelta("ProductViewed")).isCloseTo(0.1, within(1e-9));
        assertThat(policy.catalogDelta("LikeAdded")).isCloseTo(0.2, within(1e-9));
        assertThat(policy.catalogDelta("LikeRemoved")).isCloseTo(-0.2, within(1e-9));
        assertThat(policy.catalogDelta("UnknownType")).isZero();
    }

    @DisplayName("주문 delta 는 가중치 × 수량이다.")
    @Test
    void orderDelta() {
        assertThat(policy.orderDelta(3)).isCloseTo(1.8, within(1e-9));
        assertThat(policy.orderDelta(1)).isCloseTo(0.6, within(1e-9));
    }
}
