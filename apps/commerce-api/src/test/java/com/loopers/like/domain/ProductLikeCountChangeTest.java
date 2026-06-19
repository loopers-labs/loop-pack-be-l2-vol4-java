package com.loopers.like.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductLikeCountChangeTest {

    @DisplayName("좋아요 수 증가 변경 기록을 생성한다.")
    @Test
    void createsIncreaseChange() {
        // arrange
        Long productId = 101L;

        // act
        ProductLikeCountChange change = ProductLikeCountChange.from(LikeChange.increased(productId));

        // assert
        assertAll(
            () -> assertThat(change.getProductId()).isEqualTo(productId),
            () -> assertThat(change.getChangeAmount()).isEqualTo(1)
        );
    }

    @DisplayName("좋아요 수 감소 변경 기록을 생성한다.")
    @Test
    void createsDecreaseChange() {
        // arrange
        Long productId = 101L;

        // act
        ProductLikeCountChange change = ProductLikeCountChange.from(LikeChange.decreased(productId));

        // assert
        assertAll(
            () -> assertThat(change.getProductId()).isEqualTo(productId),
            () -> assertThat(change.getChangeAmount()).isEqualTo(-1)
        );
    }
}
