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

    @DisplayName("좋아요 모델 생성 시")
    @Nested
    class Create {

        @DisplayName("유저 ID와 상품 ID를 입력하면 정상 생성된다")
        @Test
        void createsLike_whenAllFieldsAreValid() {
            // when
            LikeModel like = new LikeModel(1L, 100L);

            // then
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(1L),
                () -> assertThat(like.getProductId()).isEqualTo(100L)
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () -> new LikeModel(null, 100L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () -> new LikeModel(1L, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
