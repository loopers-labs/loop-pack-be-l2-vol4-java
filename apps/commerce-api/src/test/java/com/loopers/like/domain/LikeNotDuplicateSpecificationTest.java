package com.loopers.like.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LikeNotDuplicateSpecificationTest {

    private final LikeNotDuplicateSpecification spec = new LikeNotDuplicateSpecification();

    @DisplayName("isSatisfiedBy를 호출할 때,")
    @Nested
    class IsSatisfiedBy {

        @DisplayName("좋아요가 없으면, true를 반환한다.")
        @Test
        void returnsTrue_whenLikeNotExists() {
            // act & assert
            assertThat(spec.isSatisfiedBy(Optional.empty())).isTrue();
        }

        @DisplayName("이미 좋아요했으면, false를 반환한다.")
        @Test
        void returnsFalse_whenLikeAlreadyExists() {
            // arrange
            LikeModel existing = new LikeModel(1L, 2L);

            // act & assert
            assertThat(spec.isSatisfiedBy(Optional.of(existing))).isFalse();
        }
    }
}
