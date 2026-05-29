package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeModelTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId와 productId가 저장된다.")
        @Test
        void storesUserIdAndProductId() {
            // arrange
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            // act
            LikeModel like = new LikeModel(userId, productId);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("서로 다른 (userId, productId) 조합은 독립적인 LikeModel을 생성한다.")
        @Test
        void createsSeparateInstances_whenDifferentUserAndProduct() {
            // arrange
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            // act
            LikeModel like1 = new LikeModel(userId1, productId);
            LikeModel like2 = new LikeModel(userId2, productId);

            // assert
            assertAll(
                () -> assertThat(like1.getUserId()).isEqualTo(userId1),
                () -> assertThat(like2.getUserId()).isEqualTo(userId2),
                () -> assertThat(like1.getProductId()).isEqualTo(like2.getProductId())
            );
        }
    }
}
