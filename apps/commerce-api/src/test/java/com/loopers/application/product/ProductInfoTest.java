package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductInfoTest {

    @DisplayName("ProductModel 과 BrandModel 로부터 ProductInfo 를 생성할 때, ")
    @Nested
    class From {

        @DisplayName("각 모델의 필드를 ProductInfo 로 정확히 매핑한다.")
        @Test
        void mapsFieldsFromProductModelAndBrandModel() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", BigDecimal.valueOf(150000));
            BrandModel brand = new BrandModel("Nike");

            // when
            ProductInfo result = ProductInfo.from(product, brand);

            // then
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(product.getId()),
                    () -> assertThat(result.brandName()).isEqualTo("Nike"),
                    () -> assertThat(result.name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                    () -> assertThat(result.likeCount()).isEqualTo(0L)
            );
        }
    }
}
