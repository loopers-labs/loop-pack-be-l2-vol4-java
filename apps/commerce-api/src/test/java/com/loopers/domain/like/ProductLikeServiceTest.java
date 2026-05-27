package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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

    @DisplayName("좋아요 상품 목록을 구성할 때, ")
    @Nested
    class GetLikedProductDetails {

        @DisplayName("좋아요 관계 순서대로 상품과 브랜드 정보를 조합한다.")
        @Test
        void returnsProductDetailsByProductLikes() {
            // arrange
            ProductLikeModel firstLike = new ProductLikeModel("user1234", 1L);
            ProductLikeModel secondLike = new ProductLikeModel("user1234", 2L);
            ProductModel firstProduct = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductModel secondProduct = new ProductModel(20L, "셔츠", "가벼운 셔츠", 20_000L, 5);
            BrandModel firstBrand = new BrandModel("Loopers", "감성 이커머스 브랜드");
            BrandModel secondBrand = new BrandModel("Daily", "데일리 브랜드");

            // act
            List<ProductDetail> results = productLikeService.getLikedProductDetails(
                List.of(firstLike, secondLike),
                Map.of(1L, firstProduct, 2L, secondProduct),
                Map.of(10L, firstBrand, 20L, secondBrand)
            );

            // assert
            assertAll(
                () -> assertThat(results).hasSize(2),
                () -> assertThat(results.get(0).product()).isSameAs(firstProduct),
                () -> assertThat(results.get(0).brand()).isSameAs(firstBrand),
                () -> assertThat(results.get(1).product()).isSameAs(secondProduct),
                () -> assertThat(results.get(1).brand()).isSameAs(secondBrand)
            );
        }
    }
}
