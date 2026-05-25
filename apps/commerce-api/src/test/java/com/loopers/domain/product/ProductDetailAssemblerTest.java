package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductDetailAssemblerTest {

    @DisplayName("ProductModel 과 BrandModel 을 조합할 때,")
    @Nested
    class Assemble {

        @DisplayName("브랜드명이 포함된 ProductDetail 을 반환한다.")
        @Test
        void returnsProductDetail_containingBothDomainObjects() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", BigDecimal.valueOf(150000), 10L);
            BrandModel brand = new BrandModel("Nike");

            // when
            ProductDetail result = ProductDetailAssembler.assemble(product, brand);

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

    @DisplayName("상품 목록과 BrandModel 맵을 조합할 때,")
    @Nested
    class AssembleAll {

        @DisplayName("각 상품을 해당 브랜드와 매핑해 ProductDetail 목록을 반환한다.")
        @Test
        void returnsProductDetailList_mappedByBrandId() {
            // given
            // brand id=0L(BaseEntity 기본값)과 product.getBrandId()를 일치시킴
            Map<Long, BrandModel> brandMap = Map.of(0L, new BrandModel("Nike"));
            List<ProductModel> products = List.of(
                    new ProductModel(0L, "상품A", BigDecimal.valueOf(10000), 5L),
                    new ProductModel(0L, "상품B", BigDecimal.valueOf(20000), 3L)
            );

            // when
            List<ProductDetail> result = ProductDetailAssembler.assembleAll(products, brandMap);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result).allMatch(d -> d.brandName().equals("Nike"))
            );
        }

        @DisplayName("상품이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoProducts() {
            // when
            List<ProductDetail> result = ProductDetailAssembler.assembleAll(List.of(), Map.of());

            // then
            assertThat(result).isEmpty();
        }
    }
}
