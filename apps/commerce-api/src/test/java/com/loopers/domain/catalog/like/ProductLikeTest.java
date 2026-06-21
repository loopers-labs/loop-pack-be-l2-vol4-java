package com.loopers.domain.catalog.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductLikeTest {

    @DisplayName("상품 좋아요를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("사용자 ID와 상품 ID가 주어지면 정상 생성된다.")
        @Test
        void createsProductLike_whenUserIdAndProductIdAreProvided() {
            // act
            ProductLike productLike = new ProductLike("user1", 1L);

            // assert
            assertAll(
                () -> assertThat(productLike.getUserId()).isEqualTo("user1"),
                () -> assertThat(productLike.getProductId()).isEqualTo(1L)
            );
        }

        @DisplayName("사용자 ID가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductLike(" ", 1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 ID가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductLike("user1", null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
