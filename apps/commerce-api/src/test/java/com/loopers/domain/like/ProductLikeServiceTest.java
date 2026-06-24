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
        @DisplayName("처음 누른 좋아요이면, 좋아요 관계를 만든다.")
        @Test
        void createsLike_whenLikeDoesNotExist() {
            // arrange

            // act
            ProductLikeResult result = productLikeService.createLike("user1234", 1L, Optional.empty());

            // assert
            assertAll(
                () -> assertThat(result.created()).isTrue(),
                () -> assertThat(result.productLike().getUserLoginId()).isEqualTo("user1234"),
                () -> assertThat(result.productLike().getProductId()).isEqualTo(1L)
            );
        }

        @DisplayName("이미 누른 좋아요이면, 새 좋아요 관계를 만들지 않는다.")
        @Test
        void doesNotCreateLike_whenLikeAlreadyExists() {
            // arrange
            ProductLike productLike = new ProductLike("user1234", 1L);

            // act
            ProductLikeResult result = productLikeService.createLike(
                "user1234",
                1L,
                Optional.of(productLike)
            );

            // assert
            assertAll(
                () -> assertThat(result.created()).isFalse(),
                () -> assertThat(result.productLike()).isSameAs(productLike)
            );
        }
    }

    @DisplayName("상품 좋아요를 취소할 때, ")
    @Nested
    class UnlikeProduct {
        @DisplayName("누른 좋아요가 있으면, 삭제 가능 상태를 반환한다.")
        @Test
        void returnsTrue_whenLikeExists() {
            // arrange
            ProductLike productLike = new ProductLike("user1234", 1L);

            // act
            boolean result = productLikeService.deleteLike(Optional.of(productLike));

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("누른 좋아요가 없으면, 삭제하지 않는다.")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // arrange

            // act
            boolean result = productLikeService.deleteLike(Optional.empty());

            // assert
            assertThat(result).isFalse();
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
