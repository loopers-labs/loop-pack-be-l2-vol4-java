package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class ProductModelTest {

    @DisplayName("ProductModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("브랜드 식별자·이름·설명·가격·재고가 정책을 통과하면 각 값을 보존한 ProductModel이 생성된다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // arrange
            Long brandId = 1L;
            String rawName = "감성 가디건";
            String rawDescription = "포근한 감성 가디건";
            Integer rawPrice = 39_000;
            Integer rawStock = 50;

            // act
            ProductModel productModel = ProductModel.builder()
                .brandId(brandId)
                .rawName(rawName)
                .rawDescription(rawDescription)
                .rawPrice(rawPrice)
                .rawStock(rawStock)
                .build();

            // assert
            assertAll(
                () -> assertThat(productModel.getBrandId()).isEqualTo(brandId),
                () -> assertThat(productModel.getName()).isEqualTo(Name.from(rawName)),
                () -> assertThat(productModel.getDescription()).isEqualTo(rawDescription),
                () -> assertThat(productModel.getPrice()).isEqualTo(Price.from(rawPrice)),
                () -> assertThat(productModel.getStock()).isEqualTo(Stock.from(rawStock))
            );
        }

        @DisplayName("설명이 없어도(null) 나머지 값만으로 ProductModel이 생성된다.")
        @Test
        void createsProductModel_whenDescriptionIsNull() {
            // arrange
            Long brandId = 1L;
            String rawName = "감성 가디건";

            // act
            ProductModel productModel = ProductModel.builder()
                .brandId(brandId)
                .rawName(rawName)
                .rawDescription(null)
                .rawPrice(39_000)
                .rawStock(50)
                .build();

            // assert
            assertAll(
                () -> assertThat(productModel.getName()).isEqualTo(Name.from(rawName)),
                () -> assertThat(productModel.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameViolatesPolicy() {
            // arrange
            String invalidName = "가".repeat(101);

            // act & assert
            assertThatThrownBy(() -> ProductModel.builder()
                .brandId(1L)
                .rawName(invalidName)
                .rawDescription("설명")
                .rawPrice(39_000)
                .rawStock(50)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceViolatesPolicy() {
            // arrange & act & assert
            assertThatThrownBy(() -> ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("설명")
                .rawPrice(-1)
                .rawStock(50)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockViolatesPolicy() {
            // arrange & act & assert
            assertThatThrownBy(() -> ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("설명")
                .rawPrice(39_000)
                .rawStock(-1)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("ProductModel을 수정할 때,")
    @Nested
    class Update {

        private ProductModel sampleProduct() {
            return ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("포근한 감성 가디건")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
        }

        @DisplayName("새 이름·설명·가격·재고로 값이 갱신되고 브랜드 식별자는 유지된다.")
        @Test
        void updatesFields_andKeepsBrandId() {
            // arrange
            ProductModel productModel = sampleProduct();

            // act
            productModel.update("리뉴얼 가디건", "새 설명", 42_000, 30);

            // assert
            assertAll(
                () -> assertThat(productModel.getBrandId()).isEqualTo(1L),
                () -> assertThat(productModel.getName()).isEqualTo(Name.from("리뉴얼 가디건")),
                () -> assertThat(productModel.getDescription()).isEqualTo("새 설명"),
                () -> assertThat(productModel.getPrice()).isEqualTo(Price.from(42_000)),
                () -> assertThat(productModel.getStock()).isEqualTo(Stock.from(30))
            );
        }

        @DisplayName("설명을 생략(null)하면 설명이 null로 갱신된다.")
        @Test
        void updatesDescriptionToNull_whenDescriptionIsNull() {
            // arrange
            ProductModel productModel = sampleProduct();

            // act
            productModel.update("리뉴얼 가디건", null, 42_000, 30);

            // assert
            assertThat(productModel.getDescription()).isNull();
        }

        @DisplayName("새 이름이 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameViolatesPolicy() {
            // arrange
            ProductModel productModel = sampleProduct();

            // act & assert
            assertThatThrownBy(() -> productModel.update("가".repeat(101), "새 설명", 42_000, 30))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 가격이 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPriceViolatesPolicy() {
            // arrange
            ProductModel productModel = sampleProduct();

            // act & assert
            assertThatThrownBy(() -> productModel.update("리뉴얼 가디건", "새 설명", -1, 30))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 재고가 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewStockViolatesPolicy() {
            // arrange
            ProductModel productModel = sampleProduct();

            // act & assert
            assertThatThrownBy(() -> productModel.update("리뉴얼 가디건", "새 설명", 42_000, -1))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
