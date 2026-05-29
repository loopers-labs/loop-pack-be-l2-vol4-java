package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * ProductDetailService 단위 테스트.
 *
 * <p>스타일 2 전환에 따라 Repository/Service 의존이 제거되어
 * Mockito 도 필요 없이 순수 객체만으로 검증한다.
 */
class ProductDetailServiceTest {

    private ProductDetailService sut;

    @BeforeEach
    void setUp() {
        sut = new ProductDetailService();
    }

    @DisplayName("Product 와 Brand 도메인 객체를 받으면 ProductWithBrand 로 묶어 반환한다.")
    @Test
    void returnsProductWithBrand() {
        // arrange
        ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
        BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");

        // act
        ProductWithBrand result = sut.assemble(product, brand);

        // assert
        assertAll(
            () -> assertThat(result.product()).isSameAs(product),
            () -> assertThat(result.brand()).isSameAs(brand),
            () -> assertThat(result.brand().getName()).isEqualTo("나이키")
        );
    }
}
