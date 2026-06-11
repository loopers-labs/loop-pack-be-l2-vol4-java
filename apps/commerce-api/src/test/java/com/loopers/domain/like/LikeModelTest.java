package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    @DisplayName("좋아요 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 입력으로 좋아요가 생성되면 userId와 productId가 설정된다.")
        @Test
        void createsLikeModel_withGivenUserIdAndProductId() {
            // given
            Long userId = 1L;
            Long productId = 2L;

            // when
            LikeModel like = new LikeModel(userId, productId);

            // then
            assertAll(
                    () -> assertThat(like.getUserId()).isEqualTo(userId),
                    () -> assertThat(like.getProductId()).isEqualTo(productId),
                    () -> assertThat(like.getDeletedAt()).isNull()
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenUserIdIsNull(Long userId) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new LikeModel(userId, 2L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenProductIdIsNull(Long productId) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new LikeModel(1L, productId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
