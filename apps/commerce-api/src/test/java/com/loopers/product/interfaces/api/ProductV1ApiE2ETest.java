package com.loopers.product.interfaces.api;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
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
    private final BrandAdminService brandAdminService;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, BrandAdminService brandAdminService, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandAdminService = brandAdminService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<ApiResponse<ProductV1Response.Detail>> create(ProductV1Request.Create request) {
        ParameterizedTypeReference<ApiResponse<ProductV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type);
    }

    private ResponseEntity<ApiResponse<ProductV1Response.Detail>> getById(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.GET, null, type);
    }

    @DisplayName("POST /api/v1/products")
    @Nested
    class Create {

        @Test
        @DisplayName("유효한 요청으로 등록하면 200 과 상품 정보를 반환한다")
        void givenValidRequest_whenCreate_thenReturnsProduct() {
            ProductV1Request.Create request =
                    new ProductV1Request.Create(brandId, "셔츠", "캐주얼 셔츠", 29_000L, 50);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = create(request);

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
            ProductV1Request.Create request =
                    new ProductV1Request.Create(9999L, "셔츠", "설명", 29_000L, 50);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("이름이 비어있으면 400 BAD_REQUEST 응답을 받는다")
        void givenBlankName_whenCreate_thenThrowsBadRequest() {
            ProductV1Request.Create request =
                    new ProductV1Request.Create(brandId, "  ", "설명", 29_000L, 50);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("가격이 음수이면 400 BAD_REQUEST 응답을 받는다")
        void givenNegativePrice_whenCreate_thenThrowsBadRequest() {
            ProductV1Request.Create request =
                    new ProductV1Request.Create(brandId, "셔츠", "설명", -1L, 50);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("존재하는 productId 로 조회하면 200 과 상품 정보를 반환한다")
        void givenExistingProductId_whenGet_thenReturnsProduct() {
            Long productId = create(new ProductV1Request.Create(brandId, "셔츠", "설명", 29_000L, 50))
                    .getBody().data().id();

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(productId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("셔츠")
            );
        }

        @Test
        @DisplayName("존재하지 않는 productId 로 조회하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingProductId_whenGet_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(9999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetAll {

        private ResponseEntity<ApiResponse<List<ProductV1Response.Detail>>> list(String sortQuery) {
            ParameterizedTypeReference<ApiResponse<List<ProductV1Response.Detail>>> type = new ParameterizedTypeReference<>() {};
            String url = sortQuery == null ? ENDPOINT : ENDPOINT + "?sort=" + sortQuery;
            return testRestTemplate.exchange(url, HttpMethod.GET, null, type);
        }

        @Test
        @DisplayName("기본 sort 옵션(LATEST) 로 목록을 조회한다")
        void givenSavedProducts_whenGetAllWithDefaultSort_thenReturnsLatestOrder() throws Exception {
            create(new ProductV1Request.Create(brandId, "A", "설명", 1_000L, 10));
            Thread.sleep(10);
            create(new ProductV1Request.Create(brandId, "B", "설명", 2_000L, 10));
            Thread.sleep(10);
            create(new ProductV1Request.Create(brandId, "C", "설명", 3_000L, 10));

            ResponseEntity<ApiResponse<List<ProductV1Response.Detail>>> response = list(null);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(ProductV1Response.Detail::name)
                            .containsExactly("C", "B", "A")
            );
        }

        @Test
        @DisplayName("PRICE_ASC sort 옵션으로 목록을 조회한다")
        void givenSavedProducts_whenGetAllWithPriceAscSort_thenReturnsPriceAscOrder() {
            create(new ProductV1Request.Create(brandId, "비싼것", "설명", 50_000L, 10));
            create(new ProductV1Request.Create(brandId, "중간", "설명", 30_000L, 10));
            create(new ProductV1Request.Create(brandId, "싼것", "설명", 10_000L, 10));

            ResponseEntity<ApiResponse<List<ProductV1Response.Detail>>> response = list("PRICE_ASC");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(ProductV1Response.Detail::name)
                            .containsExactly("싼것", "중간", "비싼것")
            );
        }
    }

    @DisplayName("PUT /api/v1/products/{productId}")
    @Nested
    class Update {

        private ResponseEntity<ApiResponse<ProductV1Response.Detail>> update(Long productId, ProductV1Request.Update request) {
            ParameterizedTypeReference<ApiResponse<ProductV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.PUT, new HttpEntity<>(request), type);
        }

        @Test
        @DisplayName("유효한 요청으로 수정하면 200 과 변경된 상품 정보를 반환한다")
        void givenValidRequest_whenUpdate_thenReturnsUpdatedProduct() {
            Long productId = create(new ProductV1Request.Create(brandId, "셔츠", "설명", 29_000L, 50))
                    .getBody().data().id();

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response =
                    update(productId, new ProductV1Request.Update("프린트셔츠", "새 설명", 35_000L));

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("프린트셔츠"),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(35_000L)
            );
        }

        @Test
        @DisplayName("존재하지 않는 productId 를 수정하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingProductId_whenUpdate_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response =
                    update(9999L, new ProductV1Request.Update("이름", "설명", 1000L));

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
            Long productId = create(new ProductV1Request.Create(brandId, "셔츠", "설명", 29_000L, 50))
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
