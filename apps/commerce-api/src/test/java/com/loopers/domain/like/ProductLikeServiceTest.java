package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeServiceTest {

    @Mock
    private ProductLikeRepository productLikeRepository;

    @Mock
    private ProductService productService;

    private ProductLikeService productLikeService;

    @BeforeEach
    void setUp() {
        productLikeService = new ProductLikeService(productLikeRepository, productService);
    }

    @DisplayName("상품에 좋아요를 누를 때, ")
    @Nested
    class LikeProduct {
        @DisplayName("처음 누른 좋아요이면, 좋아요 관계를 저장하고 상품 좋아요 수를 증가시킨다.")
        @Test
        void savesLikeAndIncreasesProductLikeCount_whenLikeDoesNotExist() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductLikeModel productLike = new ProductLikeModel("user1234", 1L);
            when(productService.getProduct(1L)).thenReturn(product);
            when(productLikeRepository.find("user1234", 1L)).thenReturn(Optional.empty());
            when(productLikeRepository.save(any(ProductLikeModel.class))).thenReturn(productLike);

            // act
            ProductLikeModel result = productLikeService.likeProduct("user1234", 1L);

            // assert
            assertAll(
                () -> assertThat(result).isSameAs(productLike),
                () -> assertThat(product.getLikeCount()).isEqualTo(1),
                () -> verify(productLikeRepository).save(any(ProductLikeModel.class)),
                () -> verify(productService).saveProduct(product)
            );
        }

        @DisplayName("이미 누른 좋아요이면, 좋아요 수를 다시 증가시키지 않는다.")
        @Test
        void doesNotIncreaseProductLikeCount_whenLikeAlreadyExists() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductLikeModel productLike = new ProductLikeModel("user1234", 1L);
            when(productService.getProduct(1L)).thenReturn(product);
            when(productLikeRepository.find("user1234", 1L)).thenReturn(Optional.of(productLike));

            // act
            ProductLikeModel result = productLikeService.likeProduct("user1234", 1L);

            // assert
            assertAll(
                () -> assertThat(result).isSameAs(productLike),
                () -> assertThat(product.getLikeCount()).isZero(),
                () -> verify(productLikeRepository, never()).save(any()),
                () -> verify(productService, never()).saveProduct(any())
            );
        }
    }

    @DisplayName("상품 좋아요를 취소할 때, ")
    @Nested
    class UnlikeProduct {
        @DisplayName("누른 좋아요가 있으면, 좋아요 관계를 삭제하고 상품 좋아요 수를 감소시킨다.")
        @Test
        void deletesLikeAndDecreasesProductLikeCount_whenLikeExists() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            product.increaseLikeCount();
            ProductLikeModel productLike = new ProductLikeModel("user1234", 1L);
            when(productLikeRepository.find("user1234", 1L)).thenReturn(Optional.of(productLike));
            when(productService.getProduct(1L)).thenReturn(product);

            // act
            productLikeService.unlikeProduct("user1234", 1L);

            // assert
            assertAll(
                () -> assertThat(product.getLikeCount()).isZero(),
                () -> verify(productLikeRepository).delete(productLike),
                () -> verify(productService).saveProduct(product)
            );
        }

        @DisplayName("누른 좋아요가 없으면, 상품 좋아요 수를 변경하지 않는다.")
        @Test
        void doesNothing_whenLikeDoesNotExist() {
            // arrange
            when(productLikeRepository.find("user1234", 1L)).thenReturn(Optional.empty());

            // act
            productLikeService.unlikeProduct("user1234", 1L);

            // assert
            assertAll(
                () -> verify(productService, never()).getProduct(any()),
                () -> verify(productLikeRepository, never()).delete(any()),
                () -> verify(productService, never()).saveProduct(any())
            );
        }
    }
}
