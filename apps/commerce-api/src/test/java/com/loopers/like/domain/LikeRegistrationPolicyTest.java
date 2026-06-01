package com.loopers.like.domain;

import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeRegistrationPolicyTest {

    private final LikeRegistrationPolicy policy = new LikeRegistrationPolicy();

    @DisplayName("check를 호출할 때,")
    @Nested
    class Check {

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                policy.check(Optional.empty(), Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("상품은 있지만 이미 좋아요한 경우, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);
            LikeModel existing = new LikeModel(1L, product.getId());

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                policy.check(Optional.of(product), Optional.of(existing))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("상품이 있고 좋아요하지 않은 경우, 예외 없이 통과한다.")
        @Test
        void doesNotThrow_whenProductExistsAndNotYetLiked() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);

            // act & assert
            assertDoesNotThrow(() ->
                policy.check(Optional.of(product), Optional.empty())
            );
        }
    }
}
