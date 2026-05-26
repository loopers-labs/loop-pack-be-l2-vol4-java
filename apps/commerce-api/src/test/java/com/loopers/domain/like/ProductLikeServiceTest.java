package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductLikeServiceTest {

    private ProductLikeService productLikeService;

    @BeforeEach
    void setUp() {
        productLikeService = new ProductLikeService();
    }

    @DisplayName("상품에 좋아요를 누를 때, ")
    @Nested
    class LikeProduct {
        @DisplayName("처음 누른 좋아요이면, 좋아요 관계를 만들고 상품 좋아요 수를 증가시킨다.")
        @Test
        void createsLikeAndIncreasesProductLikeCount_whenLikeDoesNotExist() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            ProductLikeResult result = productLikeService.likeProduct("user1234", 1L, product, Optional.empty());

            // assert
            assertAll(
                () -> assertThat(result.created()).isTrue(),
                () -> assertThat(result.productLike().getUserLoginId()).isEqualTo("user1234"),
                () -> assertThat(result.productLike().getProductId()).isEqualTo(1L),
                () -> assertThat(product.getLikeCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 누른 좋아요이면, 좋아요 수를 다시 증가시키지 않는다.")
        @Test
        void doesNotIncreaseProductLikeCount_whenLikeAlreadyExists() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductLikeModel productLike = new ProductLikeModel("user1234", 1L);

            // act
            ProductLikeResult result = productLikeService.likeProduct(
                "user1234",
                1L,
                product,
                Optional.of(productLike)
            );

            // assert
            assertAll(
                () -> assertThat(result.created()).isFalse(),
                () -> assertThat(result.productLike()).isSameAs(productLike),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }
    }

    @DisplayName("상품 좋아요를 취소할 때, ")
    @Nested
    class UnlikeProduct {
        @DisplayName("누른 좋아요가 있으면, 상품 좋아요 수를 감소시킨다.")
        @Test
        void decreasesProductLikeCount_whenLikeExists() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            product.increaseLikeCount();
            ProductLikeModel productLike = new ProductLikeModel("user1234", 1L);

            // act
            boolean result = productLikeService.unlikeProduct(product, Optional.of(productLike));

            // assert
            assertAll(
                () -> assertThat(result).isTrue(),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("누른 좋아요가 없으면, 상품 좋아요 수를 변경하지 않는다.")
        @Test
        void doesNothing_whenLikeDoesNotExist() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            boolean result = productLikeService.unlikeProduct(product, Optional.empty());

            // assert
            assertAll(
                () -> assertThat(result).isFalse(),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }
    }
}
