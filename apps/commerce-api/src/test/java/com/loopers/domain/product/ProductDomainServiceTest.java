package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDomainServiceTest {

    private ProductDomainService productDomainService;

    @BeforeEach
    void setUp() {
        productDomainService = new ProductDomainService();
    }

    @DisplayName("Product와 Brand를 조합할 때, 브랜드 이름을 포함한 ProductDetail이 반환된다.")
    @Test
    void combineWithBrand_returnsProductDetailWithBrandName() {
        // arrange
        ProductModel product = new ProductModel("에어포스1", 139000L, 1L);
        BrandModel brand = new BrandModel("나이키");
        setId(product, 1L);
        setId(brand, 1L);

        // act
        ProductDetail detail = productDomainService.combineWithBrand(product, brand, 0);

        // assert
        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.name()).isEqualTo("에어포스1");
        assertThat(detail.price()).isEqualTo(139000L);
        assertThat(detail.brandId()).isEqualTo(1L);
        assertThat(detail.brandName()).isEqualTo("나이키");
        assertThat(detail.likeCount()).isZero();
    }

    @DisplayName("좋아요가 있는 상품을 Brand와 조합하면, likeCount가 그대로 반영된다.")
    @Test
    void combineWithBrand_reflectsLikeCount() {
        // arrange
        ProductModel product = new ProductModel("에어맥스90", 159000L, 2L);
        BrandModel brand = new BrandModel("나이키");
        setId(product, 2L);
        setId(brand, 2L);

        // act
        ProductDetail detail = productDomainService.combineWithBrand(product, brand, 2);

        // assert
        assertThat(detail.likeCount()).isEqualTo(2);
        assertThat(detail.brandName()).isEqualTo("나이키");
    }

    private void setId(Object entity, long id) {
        try {
            var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
