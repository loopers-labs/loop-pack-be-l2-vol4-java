package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.product.admin.ProductAdminV1Dto;
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

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class ListProducts {

        @DisplayName("삭제된 상품을 포함해 반환하고, 각 항목에 재고 수량과 deletedAt 이 노출된다.")
        @Test
        void includesSoftDeletedProducts_withStockAndDeletedAt() {
            // given
            ProductInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductInfo chuck  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            productFacade.deleteProduct(chuck.id());

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, HttpEntity.EMPTY, responseType
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
            ProductInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, adidasId);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION + "?brandId=" + brandId, HttpMethod.GET, HttpEntity.EMPTY, responseType
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
                COLLECTION, HttpMethod.GET, HttpEntity.EMPTY, responseType
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
                COLLECTION, HttpMethod.GET, HttpEntity.EMPTY, responseType
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
            ProductInfo created = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.deleteProduct(created.id());

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.AdminProductDetail>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, HttpEntity.EMPTY, responseType, created.id()
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
                ITEM, HttpMethod.GET, HttpEntity.EMPTY, responseType, missingProductId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("PRODUCT_NOT_FOUND")
            );
        }
    }
}
