package com.loopers.like.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    @DisplayName("Like 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId, productId가 모두 유효하면, 각 필드가 올바르게 저장된다.")
        @Test
        void createsLikeModel_whenAllFieldsAreValid() {
            // arrange
            Long userId = 1L;
            Long productId = 2L;

            // act
            LikeModel like = new LikeModel(userId, productId);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new LikeModel(null, 2L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new LikeModel(1L, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
