package com.loopers.product.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.domain.ProductStatus;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/admin/products";
    private static final String ADMIN_HEADER = "X-Loopers-Admin-Id";

    private final TestRestTemplate testRestTemplate;
    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public ProductAdminV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandRepository brandRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandRepository.save(Brand.create("루퍼스", "설명", null)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ADMIN_HEADER, "admin-1");
        return headers;
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Response.AdminDetail>> createAsAdmin(ProductAdminV1Request.Create request) {
        ParameterizedTypeReference<ApiResponse<ProductAdminV1Response.AdminDetail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type);
    }

    private ProductAdminV1Request.Create createRequest() {
        return new ProductAdminV1Request.Create(brandId, "셔츠", "설명", 29_000L, "https://cdn/shirt.png", 50);
    }

    @DisplayName("POST /api/v1/admin/products")
    @Nested
    class Create {

        @Test
        @DisplayName("admin 헤더와 유효한 요청으로 등록하면 200 과 재고 포함 정보를 반환한다")
        void givenAdminAndValidRequest_whenCreate_thenReturnsAdminDetail() {
            ResponseEntity<ApiResponse<ProductAdminV1Response.AdminDetail>> response = createAsAdmin(createRequest());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(ProductStatus.ON_SALE),
                    () -> assertThat(response.getBody().data().thumbnailUrl()).isEqualTo("https://cdn/shirt.png"),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(50)
            );
        }

        @Test
        @DisplayName("admin 헤더 없이 등록하면 401 UNAUTHORIZED 이다")
        void givenNoAdminHeader_whenCreate_thenUnauthorized() {
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Response.AdminDetail>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Response.AdminDetail>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("존재하지 않는 brandId 로 등록하면 404 NOT_FOUND 이다")
        void givenNonExistingBrand_whenCreate_thenNotFound() {
            ProductAdminV1Request.Create request =
                    new ProductAdminV1Request.Create(999L, "셔츠", "설명", 29_000L, null, 50);

            ResponseEntity<ApiResponse<ProductAdminV1Response.AdminDetail>> response = createAsAdmin(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("판매 상태 전환 / 삭제")
    @Nested
    class StatusAndDelete {

        private ResponseEntity<ApiResponse<ProductAdminV1Response.AdminDetail>> getAsAdmin(Long productId) {
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Response.AdminDetail>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);
        }

        @Test
        @DisplayName("suspend 후 admin 조회 시 status=SUSPENDED 이다")
        void givenProduct_whenSuspend_thenStatusSuspended() {
            Long productId = createAsAdmin(createRequest()).getBody().data().id();

            ParameterizedTypeReference<ApiResponse<Void>> voidType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT + "/" + productId + "/suspend", HttpMethod.POST, new HttpEntity<>(adminHeaders()), voidType);

            assertThat(getAsAdmin(productId).getBody().data().status()).isEqualTo(ProductStatus.SUSPENDED);
        }

        @Test
        @DisplayName("resume 후 admin 조회 시 status=ON_SALE 이다")
        void givenSuspendedProduct_whenResume_thenStatusOnSale() {
            Long productId = createAsAdmin(createRequest()).getBody().data().id();
            ParameterizedTypeReference<ApiResponse<Void>> voidType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT + "/" + productId + "/suspend", HttpMethod.POST, new HttpEntity<>(adminHeaders()), voidType);

            testRestTemplate.exchange(ENDPOINT + "/" + productId + "/resume", HttpMethod.POST, new HttpEntity<>(adminHeaders()), voidType);

            assertThat(getAsAdmin(productId).getBody().data().status()).isEqualTo(ProductStatus.ON_SALE);
        }

        @Test
        @DisplayName("삭제하면 200 후 admin 조회에서 404 이다")
        void givenProduct_whenDelete_thenNotFoundAfterwards() {
            Long productId = createAsAdmin(createRequest()).getBody().data().id();

            ParameterizedTypeReference<ApiResponse<Void>> voidType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> deleteResponse =
                    testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), voidType);

            assertAll(
                    () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getAsAdmin(productId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }
}
