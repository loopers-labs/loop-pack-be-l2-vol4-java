package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
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
import org.springframework.http.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String PRODUCTS_PATH = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createBrand(String name) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, "설명")),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createProduct(Long brandId, String name, Long price) {
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCTS_PATH, HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateProductRequest(brandId, name, "설명", null, price, 10)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private void deleteProduct(Long productId) {
        testRestTemplate.exchange(PRODUCTS_PATH + "/" + productId, HttpMethod.DELETE, HttpEntity.EMPTY, Object.class);
    }

    private static final ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> getList(String query) {
        return testRestTemplate.exchange(PRODUCTS_PATH + query, HttpMethod.GET, HttpEntity.EMPTY, LIST_TYPE);
    }

    @DisplayName("GET /api/v1/products — 목록·정렬·필터")
    @Nested
    class GetProducts {

        @DisplayName("정렬을 가격 오름차순으로 주면, 가격 오름차순으로 정렬되어 반환된다.")
        @Test
        void sortsByPriceAsc() {
            Long brandId = createBrand("나이키");
            createProduct(brandId, "B", 3000L);
            createProduct(brandId, "A", 1000L);
            createProduct(brandId, "C", 2000L);

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = getList("?sort=PRICE_ASC");

            List<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(data).hasSize(3),
                    () -> assertThat(data).extracting(ProductV1Dto.ProductResponse::price)
                            .containsExactly(1000L, 2000L, 3000L)
            );
        }

        @DisplayName("브랜드 필터를 주면, 해당 브랜드의 상품만 반환된다.")
        @Test
        void filtersByBrand() {
            Long nike = createBrand("나이키");
            Long adidas = createBrand("아디다스");
            createProduct(nike, "에어맥스", 139000L);
            createProduct(adidas, "울트라부스트", 159000L);

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = getList("?brandId=" + nike);

            List<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data).hasSize(1),
                    () -> assertThat(data.get(0).brandId()).isEqualTo(nike)
            );
        }

        @DisplayName("삭제된(비활성) 상품은 목록에서 제외된다.")
        @Test
        void excludesDeleted() {
            Long brandId = createBrand("나이키");
            Long keep = createProduct(brandId, "유지", 1000L);
            Long removed = createProduct(brandId, "삭제", 2000L);
            deleteProduct(removed);

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = getList("");

            List<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                    () -> assertThat(data).hasSize(1),
                    () -> assertThat(data.get(0).id()).isEqualTo(keep)
            );
        }

        @DisplayName("size가 상한(100)을 초과하면, 400을 반환한다. (§7.2)")
        @Test
        void returns400_whenSizeOverLimit() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    PRODUCTS_PATH + "?size=101", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 정렬 값을 주면, 400을 반환한다.")
        @Test
        void returns400_whenInvalidSort() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    PRODUCTS_PATH + "?sort=INVALID", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
