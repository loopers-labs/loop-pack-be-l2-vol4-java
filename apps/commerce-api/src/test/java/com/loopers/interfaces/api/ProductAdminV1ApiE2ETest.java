package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/products";
    private static final String ADMIN_LDAP = "loopers.admin";

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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ProductEntity saveProduct(Long brandId, String name, BigDecimal price, long stock) {
        ProductEntity product = productJpaRepository.save(new ProductEntity(brandId, name, price));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), stock));
        return product;
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("어드민 헤더가 있으면, 상품 목록을 반환한다.")
        @Test
        void returnsProductList_whenAdminHeaderIsPresent() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            saveProduct(brand.getId(), "상품1", BigDecimal.valueOf(10000), 5L);
            saveProduct(brand.getId(), "상품2", BigDecimal.valueOf(20000), 3L);
            saveProduct(brand.getId(), "상품3", BigDecimal.valueOf(30000), 1L);

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=2",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> page = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) page.get("content")).hasSize(2),
                () -> assertThat(page.get("totalElements")).isEqualTo(3),
                () -> assertThat(page.get("totalPages")).isEqualTo(2)
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품을 조회하면, 재고 포함 상세 정보를 반환한다.")
        @Test
        void returnsProductWithStock_whenProductExists() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "청바지", BigDecimal.valueOf(50000), 15L);

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId(),
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(15L),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 요청이면, 상품이 생성된다.")
        @Test
        void createsProduct_whenValidRequest() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            String body = """
                {"brandId": %d, "name": "새 상품", "price": 29000, "stock": 10}
                """.formatted(brand.getId());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("새 상품"),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(10L)
            );
        }

        @DisplayName("가격이 음수이면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPriceIsNegative() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            String body = """
                {"brandId": %d, "name": "상품", "price": -1, "stock": 10}
                """.formatted(brand.getId());

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 브랜드 ID를 주면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            String body = """
                {"brandId": 9999, "name": "상품", "price": 1000, "stock": 10}
                """;

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            String body = """
                {"brandId": 1, "name": "상품", "price": 1000, "stock": 10}
                """;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("존재하는 상품을 수정하면, 수정된 정보가 반환된다.")
        @Test
        void updatesProduct_whenProductExists() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "청바지", BigDecimal.valueOf(50000), 5L);
            String body = """
                {"brandId": %d, "name": "수정 청바지", "price": 45000, "stock": 20}
                """.formatted(brand.getId());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId(),
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정 청바지"),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(20L)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            String body = """
                {"brandId": 1, "name": "이름", "price": 1000, "stock": 5}
                """;

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품을 삭제하면, 200 응답을 반환한다.")
        @Test
        void deletesProduct_whenProductExists() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "청바지", BigDecimal.valueOf(50000), 5L);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId(),
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("이미 삭제된 상품을 삭제하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductAlreadyDeleted() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = saveProduct(brand.getId(), "청바지", BigDecimal.valueOf(50000), 5L);
            product.delete();
            productJpaRepository.save(product);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId(),
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
