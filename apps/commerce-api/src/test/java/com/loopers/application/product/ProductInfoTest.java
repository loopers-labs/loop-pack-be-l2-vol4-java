package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.stock.StockModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductInfoTest {

    @DisplayName("ProductModel, BrandModel, StockModel, ProductStatsModel 로부터 ProductInfo 를 생성할 때, ")
    @Nested
    class From {

        @DisplayName("각 모델의 필드를 ProductInfo 로 정확히 매핑하고, 재고가 있으면 inStock 이 true 이다.")
        @Test
        void mapsFieldsAndReturnsInStockTrue_whenStockIsPositive() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", BigDecimal.valueOf(150000));
            BrandModel brand = new BrandModel("Nike");
            StockModel stock = new StockModel(null, 5L);
            ProductStatsModel stats = new ProductStatsModel(product);

            // when
            ProductInfo result = ProductInfo.from(product, brand, stock, stats);

            // then
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(product.getId()),
                    () -> assertThat(result.brandName()).isEqualTo("Nike"),
                    () -> assertThat(result.name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                    () -> assertThat(result.likeCount()).isEqualTo(0L),
                    () -> assertThat(result.inStock()).isTrue()
            );
        }

        @DisplayName("재고 수량이 0 이면 inStock 이 false 이다.")
        @Test
        void returnsInStockFalse_whenStockIsZero() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", BigDecimal.valueOf(150000));
            BrandModel brand = new BrandModel("Nike");
            StockModel stock = new StockModel(null, 0L);
            ProductStatsModel stats = new ProductStatsModel(product);

            // when
            ProductInfo result = ProductInfo.from(product, brand, stock, stats);

            // then
            assertThat(result.inStock()).isFalse();
        }
    }
}
