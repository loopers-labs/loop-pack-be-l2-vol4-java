package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.FakeBrandRepository;
import com.loopers.domain.like.FakeLikeRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductDisplayServiceTest {

    private ProductRepository productRepository;
    private BrandRepository brandRepository;
    private LikeRepository likeRepository;
    private ProductDisplayService productDisplayService;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        brandRepository = new FakeBrandRepository();
        likeRepository = new FakeLikeRepository();
        productDisplayService = new ProductDisplayService(productRepository, brandRepository, likeRepository);
    }

    @DisplayName("상품 상세를 조회할 때, ")
    @Nested
    class GetProductDetail {

        @DisplayName("상품 + 브랜드 정보 + 좋아요 수가 함께 조합되어 반환된다.")
        @Test
        void composesProductWithBrandAndLikeCount() {
            // arrange
            Brand brand = brandRepository.save(Brand.create("나이키", "스포츠"));
            Product product = productRepository.save(
                Product.create("운동화", "가벼운 운동화", Money.of(50_000L), 10, brand.getId()));
            likeRepository.save(Like.of(1L, product.getId()));
            likeRepository.save(Like.of(2L, product.getId()));

            // act
            ProductDetail detail = productDisplayService.getProductDetail(product.getId());

            // assert
            assertThat(detail.product().getName()).isEqualTo("운동화");
            assertThat(detail.brand().getName()).isEqualTo("나이키");
            assertThat(detail.likeCount()).isEqualTo(2L);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> productDisplayService.getProductDetail(999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
