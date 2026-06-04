package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @DisplayName("Like 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 값이면 userId/productId 가 설정된다.")
        @Test
        void createsNewLike_whenValid() {
            // act
            Like like = Like.create(USER_ID, PRODUCT_ID);

            // assert
            assertThat(like.getUserId()).isEqualTo(USER_ID);
            assertThat(like.getProductId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Like.create(null, PRODUCT_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Like.create(USER_ID, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Like 를 restore 로 복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("정상 값이면 userId/productId 가 복원된다.")
        @Test
        void restoresLike_whenValid() {
            // act
            Like like = Like.restore(USER_ID, PRODUCT_ID);

            // assert
            assertThat(like.getUserId()).isEqualTo(USER_ID);
            assertThat(like.getProductId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Like.restore(null, PRODUCT_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
