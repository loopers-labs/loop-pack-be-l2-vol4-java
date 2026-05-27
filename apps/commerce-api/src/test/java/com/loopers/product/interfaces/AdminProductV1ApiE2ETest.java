package com.loopers.product.interfaces;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.response.ApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/admin/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/admin/products")
    @Nested
    class CreateProduct {

        @DisplayName("brandId 없이 정상 요청이면, 200 OK와 생성된 ProductResponse를 반환한다.")
        @Test
        void returnsProductResponse_whenBrandIdIsNull() {
            // arrange
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest("에어맥스", "나이키 운동화", 150000L, 100, null);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandId()).isNull()
            );
        }

        @DisplayName("존재하는 brandId로 정상 요청이면, 200 OK와 생성된 ProductResponse를 반환한다.")
        @Test
        void returnsProductResponse_whenBrandExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest("에어맥스", "나이키 운동화", 150000L, 100, brand.getId());

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId())
            );
        }

        @DisplayName("존재하지 않는 brandId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // arrange
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest("에어맥스", "나이키 운동화", 150000L, 100, 999L);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("name이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest("", "나이키 운동화", 150000L, 100, null);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PATCH /api/v1/admin/products/{id}")
    @Nested
    class UpdateProduct {

        @DisplayName("정상 요청이면, 200 OK와 수정된 ProductResponse를 반환한다.")
        @Test
        void returnsUpdatedProductResponse_whenRequestIsValid() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            AdminProductV1Dto.UpdateRequest request = new AdminProductV1Dto.UpdateRequest("조던1", "나이키 농구화", 200000L, 50);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("조던1"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(200000L),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(50)
            );
        }

        @DisplayName("존재하지 않는 productId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            AdminProductV1Dto.UpdateRequest request = new AdminProductV1Dto.UpdateRequest("조던1", "나이키 농구화", 200000L, 50);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("name이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            AdminProductV1Dto.UpdateRequest request = new AdminProductV1Dto.UpdateRequest("", "나이키 농구화", 200000L, 50);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/admin/products/{id}")
    @Nested
    class DeleteProduct {

        @DisplayName("정상 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenRequestIsValid() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.DELETE, new HttpEntity<>(null), Void.class);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("존재하지 않는 productId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
