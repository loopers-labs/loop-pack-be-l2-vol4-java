package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.FakeBrandRepository;
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
    private ProductLikeStatRepository productLikeStatRepository;
    private ProductDisplayService productDisplayService;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        brandRepository = new FakeBrandRepository();
        productLikeStatRepository = new FakeProductLikeStatRepository();
        productDisplayService = new ProductDisplayService(productRepository, brandRepository, productLikeStatRepository);
    }

    @DisplayName("상품 상세를 조회할 때, ")
    @Nested
    class GetProductDetail {

        @DisplayName("상품 + 브랜드 정보 + 좋아요 수(stat)가 함께 조합되어 반환된다.")
        @Test
        void composesProductWithBrandAndLikeCount() {
            // arrange
            Brand brand = brandRepository.save(Brand.create("나이키", "스포츠"));
            Product product = productRepository.save(
                Product.create("운동화", "가벼운 운동화", Money.of(50_000L), 10, brand.getId()));
            productLikeStatRepository.save(ProductLikeStat.of(product.getId(), brand.getId(), 2L));

            // act
            ProductDetail detail = productDisplayService.getProductDetail(product.getId());

            // assert
            assertThat(detail.product().getName()).isEqualTo("운동화");
            assertThat(detail.brand().getName()).isEqualTo("나이키");
            assertThat(detail.likeCount()).isEqualTo(2L);
        }

        @DisplayName("stat 이 없는 상품이면 likeCount 는 0 으로 채워진다.")
        @Test
        void defaultsLikeCountToZero_whenStatMissing() {
            // arrange
            Brand brand = brandRepository.save(Brand.create("아디다스", "스포츠"));
            Product product = productRepository.save(
                Product.create("러닝화", "가벼운 러닝화", Money.of(60_000L), 5, brand.getId()));
            // stat 미생성 (백필 누락 시뮬레이션)

            // act
            ProductDetail detail = productDisplayService.getProductDetail(product.getId());

            // assert
            assertThat(detail.likeCount()).isZero();
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
