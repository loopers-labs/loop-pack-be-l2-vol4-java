package com.loopers.like.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeChangeTest {

    @DisplayName("좋아요 수 증가 변경을 생성한다.")
    @Test
    void createsIncreasedChange() {
        // arrange
        Long productId = 101L;

        // act
        LikeChange change = LikeChange.increased(productId);

        // assert
        assertAll(
            () -> assertThat(change.productId()).isEqualTo(productId),
            () -> assertThat(change.countChangeAmount()).isEqualTo(1),
            () -> assertThat(change.hasCountChange()).isTrue()
        );
    }

    @DisplayName("좋아요 수 감소 변경을 생성한다.")
    @Test
    void createsDecreasedChange() {
        // arrange
        Long productId = 101L;

        // act
        LikeChange change = LikeChange.decreased(productId);

        // assert
        assertAll(
            () -> assertThat(change.productId()).isEqualTo(productId),
            () -> assertThat(change.countChangeAmount()).isEqualTo(-1),
            () -> assertThat(change.hasCountChange()).isTrue()
        );
    }

    @DisplayName("좋아요 수 변경 없음 상태를 생성한다.")
    @Test
    void createsUnchangedChange() {
        // arrange
        Long productId = 101L;

        // act
        LikeChange change = LikeChange.unchanged(productId);

        // assert
        assertAll(
            () -> assertThat(change.productId()).isEqualTo(productId),
            () -> assertThat(change.countChangeAmount()).isZero(),
            () -> assertThat(change.hasCountChange()).isFalse()
        );
    }
}
