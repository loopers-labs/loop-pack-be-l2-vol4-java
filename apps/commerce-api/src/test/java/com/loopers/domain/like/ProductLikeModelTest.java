package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductLikeModelTest {

    @DisplayName("상품 좋아요 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsProductLikeModel_whenAllFieldsAreValid() {
            // arrange
            String userLoginId = "user1234";
            Long productId = 1L;

            // act
            ProductLikeModel productLike = new ProductLikeModel(userLoginId, productId);

            // assert
            assertAll(
                () -> assertThat(productLike.getUserLoginId()).isEqualTo(userLoginId),
                () -> assertThat(productLike.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("회원 로그인 ID가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserLoginIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductLikeModel(" ", 1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 ID가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductLikeModel("user1234", null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
