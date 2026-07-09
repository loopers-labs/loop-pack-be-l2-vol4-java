package com.loopers.application.like;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class LikeCountEventListenerTest {

    @InjectMocks
    private LikeCountEventListener listener;

    @Mock private ProductRepository productRepository;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @DisplayName("ProductLikedEvent 수신 시,")
    @Nested
    class OnProductLiked {

        @DisplayName("products.like_count를 1 증가시킨다.")
        @Test
        void incrementsLikeCount() {
            // act
            listener.on(new ProductLikedEvent(USER_ID, PRODUCT_ID));

            // assert
            then(productRepository).should().incrementLikeCount(PRODUCT_ID);
        }

        @DisplayName("집계 반영 중 예외가 발생해도 상위로 전파되지 않는다 (좋아요는 이미 커밋된 상태).")
        @Test
        void doesNotPropagateException_whenIncrementFails() {
            // arrange
            willThrow(new RuntimeException("DB 오류")).given(productRepository).incrementLikeCount(PRODUCT_ID);

            // act & assert
            assertDoesNotThrow(() -> listener.on(new ProductLikedEvent(USER_ID, PRODUCT_ID)));
        }
    }

    @DisplayName("ProductUnlikedEvent 수신 시,")
    @Nested
    class OnProductUnliked {

        @DisplayName("products.like_count를 1 감소시킨다.")
        @Test
        void decrementsLikeCount() {
            // act
            listener.on(new ProductUnlikedEvent(USER_ID, PRODUCT_ID));

            // assert
            then(productRepository).should().decrementLikeCount(PRODUCT_ID);
        }

        @DisplayName("집계 반영 중 예외가 발생해도 상위로 전파되지 않는다 (좋아요 취소는 이미 커밋된 상태).")
        @Test
        void doesNotPropagateException_whenDecrementFails() {
            // arrange
            willThrow(new RuntimeException("DB 오류")).given(productRepository).decrementLikeCount(PRODUCT_ID);

            // act & assert
            assertDoesNotThrow(() -> listener.on(new ProductUnlikedEvent(USER_ID, PRODUCT_ID)));
        }
    }
}
