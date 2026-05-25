package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductEntity saveProduct(Long brandId, String name, BigDecimal price) {
        ProductEntity product = productJpaRepository.save(new ProductEntity(brandId, name, price));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));
        return product;
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID를 주면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenValidIdIsProvided() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "티셔츠", BigDecimal.valueOf(29000));

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId(),
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("티셔츠"),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID를 주면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("page, size를 주면, 상품 목록을 반환한다.")
        @Test
        void returnsProductList_whenPageAndSizeAreProvided() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            saveProduct(brand.getId(), "상품1", BigDecimal.valueOf(10000));
            saveProduct(brand.getId(), "상품2", BigDecimal.valueOf(20000));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=10",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("size가 100을 초과하면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeExceedsLimit() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=101",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("정의되지 않은 sort 값이면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSortIsInvalid() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=10&sort=invalid_sort",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 brandId로 필터링하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=10&brandId=9999",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("price_asc 정렬로 조회하면, 낮은 가격 순으로 반환된다.")
        @Test
        void returnsProducts_orderedByPriceAsc() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            saveProduct(brand.getId(), "비싼 상품", BigDecimal.valueOf(50000));
            saveProduct(brand.getId(), "싼 상품", BigDecimal.valueOf(10000));

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=10&sort=price_asc",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
