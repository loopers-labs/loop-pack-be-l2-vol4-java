package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("domain")
class ProductDomainServiceTest {

    private final ProductDomainService productDomainService = new ProductDomainService();

    private ProductModel product() {
        return new ProductModel(1L, 2L, "상품A", "설명", 1_000L, 10, 0L, null, null);
    }

    @DisplayName("상품/브랜드/좋아요수를 조합해 ProductDetail을 만든다.")
    @Test
    void composesProductDetail() {
        // arrange
        BrandModel brand = new BrandModel(2L, "나이키", "스포츠", null, null);

        // act
        ProductDetail detail = productDomainService.compose(product(), brand, 5L);

        // assert
        assertAll(
            () -> assertThat(detail.id()).isEqualTo(1L),
            () -> assertThat(detail.brandId()).isEqualTo(2L),
            () -> assertThat(detail.brandName()).isEqualTo("나이키"),
            () -> assertThat(detail.name()).isEqualTo("상품A"),
            () -> assertThat(detail.likeCount()).isEqualTo(5L)
        );
    }

    @DisplayName("브랜드가 null이면 brandName은 null이다.")
    @Test
    void brandNameIsNull_whenBrandIsNull() {
        // act
        ProductDetail detail = productDomainService.compose(product(), null, 0L);

        // assert
        assertThat(detail.brandName()).isNull();
    }
}
