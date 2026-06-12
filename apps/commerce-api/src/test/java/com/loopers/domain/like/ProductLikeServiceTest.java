package com.loopers.domain.like;

import com.loopers.domain.EntityTestSupport;
import com.loopers.domain.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ExtendWith(MockitoExtension.class)
class ProductLikeServiceTest {

    @Mock
    private ProductLikeRepository productLikeRepository;

    private ProductLikeService productLikeService;

    @BeforeEach
    void setUp() {
        productLikeService = new ProductLikeService(productLikeRepository);
    }

    @DisplayName("상품에 좋아요를 누를 때, ")
    @Nested
    class LikeProduct {
        @DisplayName("처음 누른 좋아요이면, 좋아요 관계를 만들고 상품 좋아요 수를 증가시킨다.")
        @Test
        void createsLikeAndIncreasesProductLikeCount_whenLikeDoesNotExist() {
            // arrange
            Product product = new Product(10L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            ProductLikeResult result = productLikeService.createLike("user1234", 1L, product, Optional.empty());

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
            Product product = new Product(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductLike productLike = new ProductLike("user1234", 1L);

            // act
            ProductLikeResult result = productLikeService.createLike(
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
            Product product = new Product(10L, "니트", "부드러운 니트", 30_000L, 10);
            product.increaseLikeCount();
            ProductLike productLike = new ProductLike("user1234", 1L);

            // act
            boolean result = productLikeService.deleteLike(product, Optional.of(productLike));

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
            Product product = new Product(10L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            boolean result = productLikeService.deleteLike(product, Optional.empty());

            // assert
            assertAll(
                () -> assertThat(result).isFalse(),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }
    }

    @DisplayName("좋아요 상품 목록을 정렬할 때, ")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요 관계 순서대로 상품을 반환한다.")
        @Test
        void returnsProductsByProductLikes() {
            // arrange
            ProductLike firstLike = new ProductLike("user1234", 1L);
            ProductLike secondLike = new ProductLike("user1234", 2L);
            Product firstProduct = new Product(10L, "니트", "부드러운 니트", 30_000L, 10);
            Product secondProduct = new Product(20L, "셔츠", "가벼운 셔츠", 20_000L, 5);
            EntityTestSupport.setId(firstProduct, 1L);
            EntityTestSupport.setId(secondProduct, 2L);

            // act
            List<Product> results = productLikeService.getLikedProducts(
                List.of(firstLike, secondLike),
                List.of(firstProduct, secondProduct)
            );

            // assert
            assertAll(
                () -> assertThat(results).hasSize(2),
                () -> assertThat(results.get(0)).isSameAs(firstProduct),
                () -> assertThat(results.get(1)).isSameAs(secondProduct)
            );
        }
    }
}
