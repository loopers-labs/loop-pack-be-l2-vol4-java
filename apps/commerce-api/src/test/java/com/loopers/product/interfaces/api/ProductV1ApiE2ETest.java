package com.loopers.product.interfaces.api;

import com.loopers.brand.application.BrandCommand;
import com.loopers.brand.application.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, BrandService brandService, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandCommand.Create("루퍼스", "설명")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> create(ProductV1Dto.CreateRequest request) {
        ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type);
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getById(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.GET, null, type);
    }

    @DisplayName("POST /api/v1/products")
    @Nested
    class Create {

        @Test
        @DisplayName("유효한 요청으로 등록하면 200 과 상품 정보를 반환한다")
        void givenValidRequest_whenCreate_thenReturnsProduct() {
            ProductV1Dto.CreateRequest request =
                    new ProductV1Dto.CreateRequest(brandId, "셔츠", "캐주얼 셔츠", 29_000L, 50);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = create(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("셔츠"),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(29_000L)
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 로 등록하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingBrandId_whenCreate_thenThrowsNotFound() {
            ProductV1Dto.CreateRequest request =
                    new ProductV1Dto.CreateRequest(9999L, "셔츠", "설명", 29_000L, 50);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("이름이 비어있으면 400 BAD_REQUEST 응답을 받는다")
        void givenBlankName_whenCreate_thenThrowsBadRequest() {
            ProductV1Dto.CreateRequest request =
                    new ProductV1Dto.CreateRequest(brandId, "  ", "설명", 29_000L, 50);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("가격이 음수이면 400 BAD_REQUEST 응답을 받는다")
        void givenNegativePrice_whenCreate_thenThrowsBadRequest() {
            ProductV1Dto.CreateRequest request =
                    new ProductV1Dto.CreateRequest(brandId, "셔츠", "설명", -1L, 50);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("존재하는 productId 로 조회하면 200 과 상품 정보를 반환한다")
        void givenExistingProductId_whenGet_thenReturnsProduct() {
            Long productId = create(new ProductV1Dto.CreateRequest(brandId, "셔츠", "설명", 29_000L, 50))
                    .getBody().data().id();

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getById(productId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("셔츠")
            );
        }

        @Test
        @DisplayName("존재하지 않는 productId 로 조회하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingProductId_whenGet_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getById(9999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetAll {

        private ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> list(String sortQuery) {
            ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};
            String url = sortQuery == null ? ENDPOINT : ENDPOINT + "?sort=" + sortQuery;
            return testRestTemplate.exchange(url, HttpMethod.GET, null, type);
        }

        @Test
        @DisplayName("기본 sort 옵션(LATEST) 로 목록을 조회한다")
        void givenSavedProducts_whenGetAllWithDefaultSort_thenReturnsLatestOrder() throws Exception {
            create(new ProductV1Dto.CreateRequest(brandId, "A", "설명", 1_000L, 10));
            Thread.sleep(10);
            create(new ProductV1Dto.CreateRequest(brandId, "B", "설명", 2_000L, 10));
            Thread.sleep(10);
            create(new ProductV1Dto.CreateRequest(brandId, "C", "설명", 3_000L, 10));

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = list(null);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(ProductV1Dto.ProductResponse::name)
                            .containsExactly("C", "B", "A")
            );
        }

        @Test
        @DisplayName("PRICE_ASC sort 옵션으로 목록을 조회한다")
        void givenSavedProducts_whenGetAllWithPriceAscSort_thenReturnsPriceAscOrder() {
            create(new ProductV1Dto.CreateRequest(brandId, "비싼것", "설명", 50_000L, 10));
            create(new ProductV1Dto.CreateRequest(brandId, "중간", "설명", 30_000L, 10));
            create(new ProductV1Dto.CreateRequest(brandId, "싼것", "설명", 10_000L, 10));

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = list("PRICE_ASC");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(ProductV1Dto.ProductResponse::name)
                            .containsExactly("싼것", "중간", "비싼것")
            );
        }
    }

    @DisplayName("PUT /api/v1/products/{productId}")
    @Nested
    class Update {

        private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> update(Long productId, ProductV1Dto.UpdateRequest request) {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.PUT, new HttpEntity<>(request), type);
        }

        @Test
        @DisplayName("유효한 요청으로 수정하면 200 과 변경된 상품 정보를 반환한다")
        void givenValidRequest_whenUpdate_thenReturnsUpdatedProduct() {
            Long productId = create(new ProductV1Dto.CreateRequest(brandId, "셔츠", "설명", 29_000L, 50))
                    .getBody().data().id();

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    update(productId, new ProductV1Dto.UpdateRequest("프린트셔츠", "새 설명", 35_000L));

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("프린트셔츠"),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(35_000L)
            );
        }

        @Test
        @DisplayName("존재하지 않는 productId 를 수정하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingProductId_whenUpdate_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    update(9999L, new ProductV1Dto.UpdateRequest("이름", "설명", 1000L));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}")
    @Nested
    class Delete {

        private ResponseEntity<ApiResponse<Void>> delete(Long productId) {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.DELETE, null, type);
        }

        @Test
        @DisplayName("존재하는 productId 를 삭제하면 200 응답 후 더 이상 조회되지 않는다")
        void givenExistingProductId_whenDelete_thenProductIsNotFoundAfterwards() {
            Long productId = create(new ProductV1Dto.CreateRequest(brandId, "셔츠", "설명", 29_000L, 50))
                    .getBody().data().id();

            ResponseEntity<ApiResponse<Void>> deleteResponse = delete(productId);

            assertAll(
                    () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getById(productId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @Test
        @DisplayName("존재하지 않는 productId 를 삭제하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingProductId_whenDelete_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<Void>> response = delete(9999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
