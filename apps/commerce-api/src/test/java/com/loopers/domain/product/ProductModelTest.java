package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @Test
    @DisplayName("좋아요 수를 증가시키면 likeCount가 1 늘어난다.")
    void increaseLikeCount_ShouldIncrementValue() {
        // given
        ProductModel product = new ProductModel(1L, "테스트 상품", new BigDecimal("1000"));

        // when
        product.increaseLikeCount();

        // then
        assertThat(product.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수가 1 이상일 때 감소시키면 likeCount가 1 줄어든다.")
    void decreaseLikeCount_ShouldDecrementValue() {
        // given
        ProductModel product = new ProductModel(1L, "테스트 상품", new BigDecimal("1000"));
        product.increaseLikeCount();
        assertThat(product.getLikeCount()).isEqualTo(1);

        // when
        product.decreaseLikeCount();

        // then
        assertThat(product.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("좋아요 수가 0일 때 감소시키면 예외가 발생한다.")
    void decreaseLikeCount_WhenZero_ShouldThrowException() {
        // given
        ProductModel product = new ProductModel(1L, "테스트 상품", new BigDecimal("1000"));

        // when & then
        assertThrows(CoreException.class, product::decreaseLikeCount);
    }
}
