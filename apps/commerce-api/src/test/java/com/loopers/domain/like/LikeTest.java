package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeTest {
    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("userId와 productId가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLike_whenUserIdAndProductIdAreProvided() {
            // arrange
            Long userId = 1L;
            Long productId = 10L;

            // act
            Like like = new Like(userId, productId);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // arrange
            Long productId = 10L;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Like(null, productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // arrange
            Long userId = 1L;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Like(userId, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
