package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRankModelTest {

    @DisplayName("MV 랭킹 행을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("productId, rank, score로 생성되고 값이 그대로 반환된다.")
        @Test
        void createsRow_whenValidValuesAreProvided() {
            // arrange & act
            WeeklyProductRankModel model = new WeeklyProductRankModel(100L, 1, 42.5);

            // assert
            assertThat(model.getProductId()).isEqualTo(100L);
            assertThat(model.getRank()).isEqualTo(1);
            assertThat(model.getScore()).isEqualTo(42.5);
        }

        @DisplayName("rank가 1 미만이면 예외가 발생한다.")
        @Test
        void throws_whenRankIsLessThanOne() {
            assertThatThrownBy(() -> new WeeklyProductRankModel(100L, 0, 42.5))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("productId가 null이면 예외가 발생한다.")
        @Test
        void throws_whenProductIdIsNull() {
            assertThatThrownBy(() -> new WeeklyProductRankModel(null, 1, 42.5))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
