package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeModelTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 memberId, productId로 생성하면, LikeModel이 정상 생성된다.")
        @Test
        void createsLike_whenValidInput() {
            // arrange
            Long memberId = 1L;
            Long productId = 2L;

            // act
            LikeModel like = new LikeModel(memberId, productId);

            // assert
            assertThat(like.getMemberId()).isEqualTo(memberId);
            assertThat(like.getProductId()).isEqualTo(productId);
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenMemberIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> new LikeModel(null, 2L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> new LikeModel(1L, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
