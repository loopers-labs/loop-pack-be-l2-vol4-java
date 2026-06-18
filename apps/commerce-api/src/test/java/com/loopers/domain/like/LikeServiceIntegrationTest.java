package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class LikeServiceIntegrationTest {

    @Autowired LikeService likeService;
    @Autowired ProductService productService;
    @Autowired LikeRepository likeRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        ProductModel product = productService.createProduct(1L, "에어맥스", "러닝화", null, 139000L);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long likesCount() {
        return productService.getProduct(productId).getLikesCount();
    }

    @Nested
    @DisplayName("좋아요를 등록할 때")
    class Like {

        @DisplayName("활성 상품에 좋아요하면, 저장되고 likesCount가 1 증가한다.")
        @Test
        void given_activeProduct_when_like_then_savedAndCountIncreased() {
            likeService.like(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isTrue(),
                    () -> assertThat(likesCount()).isEqualTo(1L)
            );
        }

        @DisplayName("같은 좋아요를 두 번 해도 멱등하게 처리되어 likesCount는 1을 유지한다.")
        @Test
        void given_alreadyLiked_when_likeAgain_then_idempotent() {
            likeService.like(USER_ID, productId);
            likeService.like(USER_ID, productId);

            assertThat(likesCount()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingProduct_when_like_then_throwsNotFound() {
            Throwable thrown = catchThrowable(() -> likeService.like(USER_ID, 9999L));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("좋아요를 취소할 때")
    class Unlike {

        @DisplayName("좋아요를 취소하면, 비활성되고 likesCount가 1 감소한다.")
        @Test
        void given_liked_when_unlike_then_inactiveAndCountDecreased() {
            likeService.like(USER_ID, productId);

            likeService.unlike(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isFalse(),
                    () -> assertThat(likesCount()).isEqualTo(0L)
            );
        }

        @DisplayName("좋아요하지 않은 상태에서 취소해도 멱등하게 처리되어 likesCount는 0을 유지한다.")
        @Test
        void given_notLiked_when_unlike_then_idempotent() {
            likeService.unlike(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isFalse(),
                    () -> assertThat(likesCount()).isEqualTo(0L)
            );
        }
    }

    @Nested
    @DisplayName("좋아요를 재등록할 때")
    class Reactivate {

        @DisplayName("좋아요→취소→재등록하면, 새 행이 아니라 같은 행이 reactivate되고 likesCount는 1이 된다.")
        @Test
        void given_unliked_when_likeAgain_then_reactivatesSameRow() {
            likeService.like(USER_ID, productId);
            Long firstId = likeRepository.findByUserIdAndProductId(USER_ID, productId).orElseThrow().getId();
            likeService.unlike(USER_ID, productId);

            likeService.like(USER_ID, productId);

            Long secondId = likeRepository.findByUserIdAndProductId(USER_ID, productId).orElseThrow().getId();
            assertAll(
                    () -> assertThat(secondId).isEqualTo(firstId),   // 같은 행 (새 INSERT 아님)
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isTrue(),
                    () -> assertThat(likesCount()).isEqualTo(1L)
            );
        }
    }
}
