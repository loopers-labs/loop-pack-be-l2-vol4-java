package com.loopers.domain.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LikeModelTest {

    @DisplayName("LikeModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId와 productId가 그대로 보존된다.")
        @Test
        void preservesUserIdAndProductId() {
            // arrange
            Long userId = 1L;
            Long productId = 2L;

            // act
            LikeModel likeModel = LikeModel.of(userId, productId);

            // assert
            assertAll(
                () -> assertThat(likeModel.getUserId()).isEqualTo(userId),
                () -> assertThat(likeModel.getProductId()).isEqualTo(productId)
            );
        }
    }
}
