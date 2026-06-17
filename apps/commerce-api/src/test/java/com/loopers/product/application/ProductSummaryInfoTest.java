package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductSummaryInfoTest {

    @DisplayName("ProductSummaryInfo 생성 시,")
    @Nested
    class From {

        @DisplayName("ProductModel, brandName, stock으로 생성하면 description 없이 필드가 정상 매핑된다.")
        @Test
        void mapsFields_withoutDescription_whenCreatedFromProductModel() {
            // arrange
            ProductModel model = new ProductModel("에어맥스", "나이키 운동화 상세 설명", 150000L, 1L);

            // act
            ProductSummaryInfo result = ProductSummaryInfo.from(model, "나이키", 50);

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.price()).isEqualTo(150000L),
                () -> assertThat(result.brandId()).isEqualTo(1L),
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.stock()).isEqualTo(50),
                () -> assertThat(result.likeCount()).isEqualTo(0L)
            );
        }
    }
}
