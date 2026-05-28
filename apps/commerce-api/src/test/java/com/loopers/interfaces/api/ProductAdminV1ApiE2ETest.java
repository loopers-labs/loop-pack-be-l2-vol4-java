package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String BRAND_URL   = "/api-admin/v1/brands";
    private static final String PRODUCT_URL = "/api-admin/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UUID createBrand() {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            BRAND_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION)),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().data().id();
    }

    private UUID createProduct(UUID brandId) {
        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
            PRODUCT_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            )),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().data().id();
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class Create {

        @DisplayName("유효한 요청으로 생성 시, 200 + AdminProductResponse를 반환한다.")
        @Test
        void returnsProduct_whenValidRequest() {
            // arrange
            UUID brandId = createBrand();

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_URL, HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateRequest(
                    brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
                )),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo(ProductFixture.NAME),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo(BrandFixture.NAME),
                () -> assertThat(response.getBody().data().totalQuantity()).isEqualTo(ProductFixture.INITIAL_QUANTITY)
            );
        }

        @DisplayName("존재하지 않는 brandId로 생성 시, 404를 반환한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_URL, HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateRequest(
                    UUID.randomUUID(), ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
                )),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("상품명이 빈값이면, 400을 반환한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            UUID brandId = createBrand();

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_URL, HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateRequest(
                    brandId, "", ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
                )),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{id}")
    @Nested
    class Get {

        @DisplayName("존재하는 상품 조회 시, 200 + 재고 수량 포함 응답을 반환한다.")
        @Test
        void returnsProduct_whenExists() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_URL + "/" + productId, HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalQuantity()).isEqualTo(ProductFixture.INITIAL_QUANTITY),
                () -> assertThat(response.getBody().data().reservedQuantity()).isZero()
            );
        }

        @DisplayName("존재하지 않는 ID 조회 시, 404를 반환한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PRODUCT_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 상품을 어드민으로 조회 시, 200을 반환한다.")
        @Test
        void returnsProduct_whenDeleted() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);
            testRestTemplate.exchange(PRODUCT_URL + "/" + productId, HttpMethod.DELETE, null, new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_URL + "/" + productId, HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetList {

        @DisplayName("상품 목록 조회 시, 200 + 페이징 결과를 반환한다.")
        @Test
        void returnsPagedList_whenProductsExist() {
            // arrange
            UUID brandId = createBrand();
            createProduct(brandId);
            createProduct(brandId);
            createProduct(brandId);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>>> response = testRestTemplate.exchange(
                PRODUCT_URL + "?page=0&size=2", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().getContent()).hasSize(2)
            );
        }

        @DisplayName("brandId 필터 조회 시, 해당 브랜드 상품만 반환된다.")
        @Test
        void returnsFilteredList_whenBrandIdProvided() {
            // arrange
            UUID brandId1 = createBrand();
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
                BRAND_URL, HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateRequest("다른브랜드", "설명")),
                new ParameterizedTypeReference<>() {}
            );
            UUID brandId2 = brandResp.getBody().data().id();

            createProduct(brandId1);
            createProduct(brandId1);
            createProduct(brandId2);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>>> response = testRestTemplate.exchange(
                PRODUCT_URL + "?brandId=" + brandId1 + "&page=0&size=10", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert — brandId1 상품 2건만
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(2)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{id}")
    @Nested
    class Update {

        @DisplayName("유효한 요청으로 수정 시, 200 + 변경된 상품 정보를 반환한다.")
        @Test
        void returnsUpdatedProduct_whenValidRequest() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_URL + "/" + productId, HttpMethod.PUT,
                new HttpEntity<>(new ProductV1Dto.UpdateRequest("변경된 상품명", "변경된 설명", 200_000L)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("변경된 상품명"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(200_000L)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{id}")
    @Nested
    class Delete {

        @DisplayName("상품 삭제 후 고객용 GET으로 조회 시, 404를 반환한다.")
        @Test
        void returnsNotFound_whenDeletedAndAccessedByCustomer() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);

            // act — 삭제
            ResponseEntity<ApiResponse<Void>> deleteResponse = testRestTemplate.exchange(
                PRODUCT_URL + "/" + productId, HttpMethod.DELETE,
                null, new ParameterizedTypeReference<>() {}
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert — 고객 조회 → 404
            ResponseEntity<ApiResponse<Void>> getResponse = testRestTemplate.exchange(
                "/api/v1/products/" + productId, HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
