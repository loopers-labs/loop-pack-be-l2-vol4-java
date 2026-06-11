package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeTest {

    @DisplayName("사용자 ID와 상품 ID가 주어지면 좋아요를 생성한다.")
    @Test
    void createsLike_whenUserIdAndProductIdAreProvided() {
        // arrange
        Long userId = 1L;
        Long productId = 1L;

        // act
        Like like = Like.create(userId, productId);

        // assert
        assertThat(like.getUserId()).isEqualTo(userId);
        assertThat(like.getProductId()).isEqualTo(productId);
    }

    @DisplayName("사용자 ID가 비어 있으면 BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUserIdIsNull() {
        // act & assert
        assertThatThrownBy(() -> Like.create(null, 1L))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품 ID가 비어 있으면 BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenProductIdIsNull() {
        // act & assert
        assertThatThrownBy(() -> Like.create(1L, null))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
