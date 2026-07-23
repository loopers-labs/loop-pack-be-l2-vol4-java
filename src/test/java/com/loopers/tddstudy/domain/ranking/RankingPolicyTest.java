package com.loopers.tddstudy.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPolicyTest {

    @Test
    @DisplayName("좋아요는 +0.2점이다")
    void liked() {
        assertThat(RankingPolicy.deltaOf("PRODUCT_LIKED")).isEqualTo(0.2);
    }

    @Test
    @DisplayName("좋아요 취소는 -0.2점이다")
    void unliked() {
        assertThat(RankingPolicy.deltaOf("PRODUCT_UNLIKED")).isEqualTo(-0.2);
    }

    @Test
    @DisplayName("주문은 건당 +0.7점이다")
    void order() {
        assertThat(RankingPolicy.deltaOf("ORDER_SALES")).isEqualTo(0.7);
    }

    @Test
    @DisplayName("모르는 이벤트 타입은 0점이다")
    void unknown() {
        assertThat(RankingPolicy.deltaOf("SOMETHING_ELSE")).isEqualTo(0.0);
    }
}
