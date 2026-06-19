package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductLikeCountTest {

    @DisplayName("상품 ID와 초기 카운트로 생성된다.")
    @Test
    void creates() {
        // arrange & act
        ProductLikeCount likeCount = new ProductLikeCount(1L, 5L);

        // assert
        assertAll(
            () -> assertThat(likeCount.getProductId()).isEqualTo(1L),
            () -> assertThat(likeCount.getCount()).isEqualTo(5L)
        );
    }

    @DisplayName("상품 ID가 없으면 예외가 발생한다.")
    @Test
    void throws_whenProductIdNull() {
        // arrange & act & assert
        assertThrows(CoreException.class, () -> new ProductLikeCount(null, 0L));
    }

    @DisplayName("카운트가 음수면 예외가 발생한다.")
    @Test
    void throws_whenCountNegative() {
        // arrange & act & assert
        assertThrows(CoreException.class, () -> new ProductLikeCount(1L, -1L));
    }
}
