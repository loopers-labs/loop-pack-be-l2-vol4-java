package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeResultTest {

    @DisplayName("isApplied()를 호출할 때,")
    @Nested
    class IsApplied {

        @DisplayName("APPLIED이면 true를 반환한다.")
        @Test
        void returnsTrue_whenApplied() {
            assertThat(LikeResult.APPLIED.isApplied()).isTrue();
        }

        @DisplayName("IGNORED이면 false를 반환한다.")
        @Test
        void returnsFalse_whenIgnored() {
            assertThat(LikeResult.IGNORED.isApplied()).isFalse();
        }
    }
}
