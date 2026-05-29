package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.product.admin.ProductAdminV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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

    private static final String COLLECTION = "/api-admin/v1/products";
    private static final String ITEM = "/api-admin/v1/products/{productId}";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public ProductAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandFacade.create("나이키", "Just Do It").id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.ADMIN_LDAP, AuthHeaders.ADMIN_LDAP_VALUE);
        return headers;
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class ListProducts {

        @DisplayName("삭제된 상품을 포함해 반환하고, 각 항목에 재고 수량과 deletedAt 이 노출된다.")
        @Test
        void includesSoftDeletedProducts_withStockAndDeletedAt() {
            // given
            ProductAdminInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductAdminInfo chuck  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            productFacade.deleteProduct(chuck.id());

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // then
            ProductAdminV1Dto.PageResponse body = response.getBody().data();
            ProductAdminV1Dto.AdminProductSummary deletedItem = body.content().stream()
                .filter(item -> item.id().equals(chuck.id()))
                .findFirst()
                .orElseThrow();
            ProductAdminV1Dto.AdminProductSummary activeItem = body.content().stream()
                .filter(item -> item.id().equals(airmax.id()))
                .findFirst()
                .orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.totalElements()).isEqualTo(2),
                () -> assertThat(deletedItem.deletedAt()).isNotNull(),
                () -> assertThat(deletedItem.stockQuantity()).isEqualTo(30),
                () -> assertThat(activeItem.deletedAt()).isNull(),
                () -> assertThat(activeItem.stockQuantity()).isEqualTo(50)
            );
        }

        @DisplayName("brandId 로 필터하면, 해당 브랜드의 상품만 반환한다.")
        @Test
        void appliesBrandFilter() {
            // given
            Long adidasId = brandFacade.create("아디다스", "Impossible is Nothing").id();
            ProductAdminInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, adidasId);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION + "?brandId=" + brandId, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // then
            ProductAdminV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.content()).extracting(ProductAdminV1Dto.AdminProductSummary::id)
                    .containsExactly(airmax.id())
            );
        }

        @DisplayName("page, size 를 지정하지 않으면 기본 page=0, size=20 으로 조회한다.")
        @Test
        void usesDefaultPageAndSize() {
            // given
            productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // then
            ProductAdminV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.page()).isEqualTo(0),
                () -> assertThat(body.size()).isEqualTo(20)
            );
        }

        @DisplayName("상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoProductExists() {
            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // then
            ProductAdminV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.content()).isEmpty(),
                () -> assertThat(body.totalElements()).isZero()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("삭제된 상품도 재고 수량과 등록·수정·삭제 일시와 함께 반환한다.")
        @Test
        void returnsSoftDeletedProduct_withStockAndOperationalInfo() {
            // given
            ProductAdminInfo created = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.deleteProduct(created.id());

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType, created.id()
            );

            // then
            ProductAdminV1Dto.AdminProductDetail body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.id()).isEqualTo(created.id()),
                () -> assertThat(body.stockQuantity()).isEqualTo(50),
                () -> assertThat(body.deletedAt()).isNotNull(),
                () -> assertThat(body.createdAt()).isNotNull(),
                () -> assertThat(body.updatedAt()).isNotNull(),
                () -> assertThat(body.brand().id()).isEqualTo(brandId)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 와 PRODUCT_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // given
            Long missingProductId = 9_999L;

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType, missingProductId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("PRODUCT_NOT_FOUND")
            );
        }
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 요청이면, 200 과 재고 수량을 포함한 신규 상품 정보를 반환한다.")
        @Test
        void returnsCreatedProduct_withStockQuantity() {
            // given
            ProductAdminV1Dto.CreateRequest request =
                new ProductAdminV1Dto.CreateRequest("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // then
            ProductAdminV1Dto.AdminProductDetail body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.id()).isNotNull(),
                () -> assertThat(body.name()).isEqualTo("에어맥스 270"),
                () -> assertThat(body.price()).isEqualTo(159_000L),
                () -> assertThat(body.stockQuantity()).isEqualTo(50),
                () -> assertThat(body.deletedAt()).isNull(),
                () -> assertThat(body.brand().id()).isEqualTo(brandId)
            );
        }

        @DisplayName("존재하지 않는 brandId 면, 404 와 BRAND_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsBrandNotFound_whenBrandDoesNotExist() {
            // given
            Long missingBrandId = brandId + 9_999L;
            ProductAdminV1Dto.CreateRequest request =
                new ProductAdminV1Dto.CreateRequest("에어맥스 270", "데일리 러닝화", 159_000L, 50, missingBrandId);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("BRAND_NOT_FOUND")
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 요청이면, 200 과 변경된 정보·재고 수량을 반환한다.")
        @Test
        void returnsUpdatedProduct_withNewStockQuantity() {
            // given
            ProductAdminInfo created = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductAdminV1Dto.UpdateRequest request =
                new ProductAdminV1Dto.UpdateRequest("에어맥스 270 SE", "스페셜 에디션", 179_000L, 80);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), responseType, created.id()
            );

            // then
            ProductAdminV1Dto.AdminProductDetail body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.name()).isEqualTo("에어맥스 270 SE"),
                () -> assertThat(body.price()).isEqualTo(179_000L),
                () -> assertThat(body.stockQuantity()).isEqualTo(80)
            );
        }

        @DisplayName("삭제된 상품을 수정하면, 404 와 PRODUCT_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsProductNotFound_whenProductIsSoftDeleted() {
            // given
            ProductAdminInfo created = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.deleteProduct(created.id());
            ProductAdminV1Dto.UpdateRequest request =
                new ProductAdminV1Dto.UpdateRequest("변경", "변경 설명", 99_000L, 10);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("PRODUCT_NOT_FOUND")
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("정상 삭제 시, 200 을 반환하고 어드민 조회에서 deletedAt 이 노출된다.")
        @Test
        void returnsOkAndSoftDeletesProduct() {
            // given
            ProductAdminInfo created = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(productFacade.getProductForAdmin(created.id()).deletedAt()).isNotNull()
            );
        }

        @DisplayName("이미 삭제된 상품을 다시 삭제해도, 200 을 반환한다 (멱등).")
        @Test
        void returnsOk_whenProductIsAlreadySoftDeleted() {
            // given
            ProductAdminInfo created = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.deleteProduct(created.id());

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }
    }
}
