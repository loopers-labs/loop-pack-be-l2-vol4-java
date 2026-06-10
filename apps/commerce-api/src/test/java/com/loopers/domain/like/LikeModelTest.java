package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    private static final Long VALID_USER_ID = 1L;
    private static final Long VALID_PRODUCT_ID = 100L;

    @DisplayName("Like 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유저 ID 와 상품 ID 가 유효하면, Like 가 정상적으로 생성된다.")
        @Test
        void createsLike_whenInputsAreValid() {
            // act
            LikeModel like = new LikeModel(VALID_USER_ID, VALID_PRODUCT_ID);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(VALID_USER_ID),
                () -> assertThat(like.getProductId()).isEqualTo(VALID_PRODUCT_ID)
            );
        }

        @DisplayName("유저 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new LikeModel(null, VALID_PRODUCT_ID));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new LikeModel(VALID_USER_ID, null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
