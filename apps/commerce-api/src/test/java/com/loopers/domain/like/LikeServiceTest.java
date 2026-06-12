package com.loopers.domain.like;

import com.loopers.domain.product.FakeProductRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeServiceTest {

    private LikeService likeService;
    private ProductService productService;
    private FakeLikeRepository fakeLikeRepository;
    private FakeProductRepository fakeProductRepository;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        productService = new ProductService(fakeProductRepository);
        likeService = new LikeService(fakeLikeRepository, productService);
    }

    private ProductModel newProduct() {
        return productService.createProduct(1L, "상품", "설명", 1000L, 5, null);
    }

    @DisplayName("좋아요 등록은 멱등이다 — 동일 사용자가 같은 상품에 여러 번 눌러도 카운트는 1 이고 행도 1 개이다.")
    @Nested
    class Like {
        @DisplayName("처음 등록하면, 좋아요 행이 생기고 상품의 likeCount 가 1 증가한다.")
        @Test
        void registers_first() {
            // arrange
            ProductModel product = newProduct();

            // act
            likeService.like(100L, product.getId());

            // assert
            assertAll(
                () -> assertThat(likeService.isLiked(100L, product.getId())).isTrue(),
                () -> assertThat(fakeProductRepository.find(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("두 번째 호출은 멱등이다 — likeCount 가 추가로 증가하지 않는다.")
        @Test
        void isIdempotent() {
            // arrange
            ProductModel product = newProduct();
            likeService.like(100L, product.getId());

            // act
            likeService.like(100L, product.getId());

            // assert
            assertThat(fakeProductRepository.find(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 상품 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                likeService.like(100L, 999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 취소도 멱등이다.")
    @Nested
    class Unlike {
        @DisplayName("등록 후 취소하면, 행이 사라지고 likeCount 가 1 감소한다.")
        @Test
        void cancels() {
            // arrange
            ProductModel product = newProduct();
            likeService.like(100L, product.getId());

            // act
            likeService.unlike(100L, product.getId());

            // assert
            assertAll(
                () -> assertThat(likeService.isLiked(100L, product.getId())).isFalse(),
                () -> assertThat(fakeProductRepository.find(product.getId()).orElseThrow().getLikeCount()).isZero()
            );
        }

        @DisplayName("취소를 두 번 호출해도 likeCount 는 0 미만으로 떨어지지 않는다.")
        @Test
        void isIdempotent_doubleCall() {
            // arrange
            ProductModel product = newProduct();
            likeService.like(100L, product.getId());
            likeService.unlike(100L, product.getId());

            // act
            likeService.unlike(100L, product.getId());

            // assert
            assertThat(fakeProductRepository.find(product.getId()).orElseThrow().getLikeCount()).isZero();
        }

        @DisplayName("등록한 적 없는 상품을 취소해도 예외 없이 통과한다.")
        @Test
        void unlikeWithoutLike_passes() {
            // arrange
            ProductModel product = newProduct();

            // act & assert
            likeService.unlike(100L, product.getId());
            assertThat(fakeProductRepository.find(product.getId()).orElseThrow().getLikeCount()).isZero();
        }
    }
}
