package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    @DisplayName("좋아요 엔티티를 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("유효한 userId/productId 면 정상 생성된다.")
        @Test
        void creates() {
            // act
            LikeModel like = new LikeModel(1L, 10L);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(1L),
                () -> assertThat(like.getProductId()).isEqualTo(10L)
            );
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new LikeModel(null, 10L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId 가 0 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdZero() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new LikeModel(1L, 0L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
