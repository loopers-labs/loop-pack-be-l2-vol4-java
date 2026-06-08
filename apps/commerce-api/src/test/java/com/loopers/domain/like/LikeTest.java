package com.loopers.domain.like;

import com.loopers.domain.like.model.Like;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeTest {

    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLike_whenAllFieldsAreValid() {
            // Arrange & Act
            Like like = Like.create(1L, 2L);

            // Assert
            assertThat(like.getUserId()).isEqualTo(1L);
            assertThat(like.getProductId()).isEqualTo(2L);
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // Arrange & Act
            CoreException result = assertThrows(CoreException.class, () ->
                Like.create(null, 2L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // Arrange & Act
            CoreException result = assertThrows(CoreException.class, () ->
                Like.create(1L, null)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
