package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductTest {

    @DisplayName("브랜드 ID와 상품 기본 정보가 주어지면, 상품을 생성한다.")
    @Test
    void createsProduct_whenBrandIdAndProductInfoAreProvided() {
        // arrange
        Long brandId = 1L;
        String name = "아이폰 16 Pro";
        String description = "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰";
        long price = 1_550_000L;

        // act
        Product product = Product.create(brandId, name, description, price);

        // assert
        assertAll(
            () -> assertThat(product.getBrandId()).isEqualTo(brandId),
            () -> assertThat(product.getName()).isEqualTo(name),
            () -> assertThat(product.getDescription()).isEqualTo(description),
            () -> assertThat(product.getPrice()).isEqualTo(price),
            () -> assertThat(product.isDeleted()).isFalse()
        );
    }

    @DisplayName("상품명이 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenNameIsBlank() {
        // arrange
        Long brandId = 1L;
        String name = " ";
        String description = "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰";
        long price = 1_550_000L;

        // act & assert
        assertThatThrownBy(() -> Product.create(brandId, name, description, price))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품 설명이 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenDescriptionIsBlank() {
        // arrange
        Long brandId = 1L;
        String name = "아이폰 16 Pro";
        String description = " ";
        long price = 1_550_000L;

        // act & assert
        assertThatThrownBy(() -> Product.create(brandId, name, description, price))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품 가격이 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenPriceIsNegative() {
        // arrange
        Long brandId = 1L;
        String name = "아이폰 16 Pro";
        String description = "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰";
        long price = -1L;

        // act & assert
        assertThatThrownBy(() -> Product.create(brandId, name, description, price))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("브랜드 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenBrandIdIsNull() {
        // arrange
        Long brandId = null;
        String name = "아이폰 16 Pro";
        String description = "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰";
        long price = 1_550_000L;

        // act & assert
        assertThatThrownBy(() -> Product.create(brandId, name, description, price))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품 기본 정보를 수정하면, 상품명과 설명과 가격이 변경된다.")
    @Test
    void updatesProductInfo_whenNameDescriptionAndPriceAreProvided() {
        // arrange
        Product product = Product.create(
            1L,
            "아이폰 16 Pro",
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            1_550_000L
        );
        String name = "아이폰 16 Pro Max";
        String description = "더 큰 화면과 향상된 배터리를 제공하는 스마트폰";
        long price = 1_900_000L;

        // act
        product.update(name, description, price);

        // assert
        assertAll(
            () -> assertThat(product.getName()).isEqualTo(name),
            () -> assertThat(product.getDescription()).isEqualTo(description),
            () -> assertThat(product.getPrice()).isEqualTo(price)
        );
    }

    @DisplayName("상품 수정 시 상품명이 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUpdateNameIsBlank() {
        // arrange
        Product product = Product.create(
            1L,
            "아이폰 16 Pro",
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            1_550_000L
        );
        String name = " ";
        String description = "더 큰 화면과 향상된 배터리를 제공하는 스마트폰";
        long price = 1_900_000L;

        // act & assert
        assertThatThrownBy(() -> product.update(name, description, price))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
