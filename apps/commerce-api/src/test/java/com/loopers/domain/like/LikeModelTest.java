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

    @DisplayName("LikeModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("userId 와 productId 가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsLikeModel_whenAllFieldsAreValid() {
            // given
            Long userId = 1L;
            Long productId = 100L;

            // when
            LikeModel like = new LikeModel(userId, productId);

            // then
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // given
            Long userId = null;
            Long productId = 100L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new LikeModel(userId, productId));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("userId 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // given
            Long userId = 1L;
            Long productId = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new LikeModel(userId, productId));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("productId 는 비어있을 수 없습니다.")
            );
        }
    }
}
