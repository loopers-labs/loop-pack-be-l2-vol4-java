package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;

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

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api-admin/v1/products";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> adminJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> jsonRequestWithoutAdmin(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    private BrandModel saveBrand(String name) {
        BrandModel brand = BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build();

        return brandJpaRepository.save(brand);
    }

    @DisplayName("상품 등록 - POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("정상 요청이면, 201 Created와 함께 productId가 응답 본문에 담겨 반환된다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("productId"),
                () -> assertThat(response.getBody().data().get("productId")).isNotNull(),
                () -> assertThat(productJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("지정한 브랜드가 존재하지 않으면, 404 Not Found로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsNotFound_whenBrandIsAbsent() {
            // arrange
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(99999L, "감성 가디건", "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("상품 이름이 100자를 초과하면, 400 Bad Request로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "가".repeat(101), "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("가격이 0 미만이면, 400 Bad Request로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenPriceIsNegative() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", -1, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("재고가 0 미만이면, 400 Bad Request로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenStockIsNegative() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 39_000, -1);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("가격과 재고가 0(경계 최소값)이면, 201 Created로 상품이 생성된다.")
        @Test
        void returnsCreated_whenPriceAndStockAreZero() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 0, 0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("productId"),
                () -> assertThat(response.getBody().data().get("productId")).isNotNull(),
                () -> assertThat(productJpaRepository.findAll()).hasSize(1)
            );
        }
    }
}
