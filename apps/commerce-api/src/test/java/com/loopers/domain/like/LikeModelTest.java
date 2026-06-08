package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeModelTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId 와 productId 가 저장된다.")
        @Test
        void createsLike() {
            // act
            LikeModel like = LikeModel.of(10L, 100L);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(10L),
                () -> assertThat(like.getProductId()).isEqualTo(100L)
            );
        }
    }
}
