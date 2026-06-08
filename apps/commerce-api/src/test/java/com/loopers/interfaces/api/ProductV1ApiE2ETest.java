package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandInfo createBrand() {
        return brandApplicationService.create("나이키", "스포츠 브랜드");
    }

    private ProductAdminInfo createProduct(Long brandId) {
        return productApplicationService.createProduct(brandId, "에어맥스", "편안한 운동화", 100_000L, 10);
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("상품 목록을 조회하면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsOk_whenProductsExist() {
            // arrange
            BrandInfo brand = createBrand();
            createProduct(brand.id());

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("brandId로 필터링하면, 200 OK와 해당 브랜드 상품 목록을 반환한다.")
        @Test
        void returnsOk_whenFilteredByBrandId() {
            // arrange
            BrandInfo brand = createBrand();
            createProduct(brand.id());

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?brandId=" + brand.id(), HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("page가 음수이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenPageIsNegative() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=-1", HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 0이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenSizeIsZero() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?size=0", HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{id}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 id로 요청하면, 200 OK와 상품 정보(브랜드/좋아요수 포함)를 반환한다.")
        @Test
        void returnsOk_whenProductExists() {
            // arrange
            BrandInfo brand = createBrand();
            ProductAdminInfo product = createProduct(brand.id());

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.id(), HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.id()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(100_000L),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("존재하지 않는 상품 id로 요청하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("id가 0이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenIdIsZero() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/0", HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
