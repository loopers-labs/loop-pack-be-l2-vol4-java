package com.loopers.product.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.shared.presentation.PageResponse;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig.class)
class ProductAdminV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api-admin/v1/products";
    private static final String ENDPOINT_PRODUCT_DETAIL = "/api-admin/v1/products/{productId}";
    private static final String ENDPOINT_PUBLIC_PRODUCT_DETAIL = "/api/v1/products/{productId}";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    ProductAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("어드민 헤더와 미삭제 브랜드의 상품 정보, 초기 재고가 주어지면 201 CREATED와 생성된 상품 정보를 반환한다.")
        @Test
        void returnsCreatedProduct_whenAdminHeaderAndValidRequestAreProvided() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            );

            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = createProduct(request, adminHeaders());

            // assert
            ProductAdminV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.brandId()).isEqualTo(brand.getId()),
                () -> assertThat(data.name()).isEqualTo("아이폰 16 Pro"),
                () -> assertThat(data.description()).isEqualTo("강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰"),
                () -> assertThat(data.price()).isEqualTo(1_550_000L),
                () -> assertThat(data.stockQuantity()).isEqualTo(10),
                () -> assertThat(data.createdAt()).isNotNull(),
                () -> assertThat(data.updatedAt()).isNotNull(),
                () -> assertThat(data.deletedAt()).isNull(),
                () -> assertThat(likeSummaryCount(data.id())).isZero()
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            );

            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = createProduct(request, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("브랜드 ID가 없으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenBrandIdIsMissing() {
            // arrange
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                null,
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            );

            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = createProduct(request, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("어드민 헤더와 존재하는 상품 ID가 주어지면 200 OK와 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenAdminHeaderAndProductIdExist() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            );
            Long productId = createProduct(request, adminHeaders()).getBody().data().id();

            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = getProduct(productId, adminHeaders());

            // assert
            ProductAdminV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(productId),
                () -> assertThat(data.brandId()).isEqualTo(brand.getId()),
                () -> assertThat(data.name()).isEqualTo("아이폰 16 Pro"),
                () -> assertThat(data.description()).isEqualTo("강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰"),
                () -> assertThat(data.price()).isEqualTo(1_550_000L),
                () -> assertThat(data.stockQuantity()).isEqualTo(10),
                () -> assertThat(data.createdAt()).isNotNull(),
                () -> assertThat(data.updatedAt()).isNotNull(),
                () -> assertThat(data.deletedAt()).isNull()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("어드민 헤더와 page, size가 주어지면 200 OK와 최신순 상품 목록을 반환한다.")
        @Test
        void returnsProductsByLatest_whenAdminHeaderAndPageQueryAreProvided() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            createProduct(new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_250_000L,
                7
            ), adminHeaders());
            createProduct(new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            ), adminHeaders());
            createProduct(new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro Max",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_900_000L,
                5
            ), adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response = getProducts(adminHeaders());

            // assert
            PageResponse<ProductAdminV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content())
                    .extracting(ProductAdminV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16 Pro Max", "아이폰 16 Pro", "아이폰 16"),
                () -> assertThat(data.totalElements()).isEqualTo(3),
                () -> assertThat(data.totalPages()).isEqualTo(1),
                () -> assertThat(data.number()).isZero(),
                () -> assertThat(data.size()).isEqualTo(20),
                () -> assertThat(data.first()).isTrue(),
                () -> assertThat(data.last()).isTrue()
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("어드민 헤더와 상품 수정 정보가 주어지면 200 OK와 수정된 상품 정보를 반환한다.")
        @Test
        void returnsUpdatedProduct_whenAdminHeaderAndValidRequestAreProvided() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Long productId = createProduct(new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            ), adminHeaders()).getBody().data().id();
            ProductAdminV1Dto.UpdateProductRequest request = new ProductAdminV1Dto.UpdateProductRequest(
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L,
                5
            );

            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = updateProduct(productId, request, adminHeaders());

            // assert
            ProductAdminV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(productId),
                () -> assertThat(data.brandId()).isEqualTo(brand.getId()),
                () -> assertThat(data.name()).isEqualTo("아이폰 16 Pro Max"),
                () -> assertThat(data.description()).isEqualTo("더 큰 화면과 향상된 배터리를 제공하는 스마트폰"),
                () -> assertThat(data.price()).isEqualTo(1_900_000L),
                () -> assertThat(data.stockQuantity()).isEqualTo(5),
                () -> assertThat(data.createdAt()).isNotNull(),
                () -> assertThat(data.updatedAt()).isNotNull(),
                () -> assertThat(data.deletedAt()).isNull()
            );
        }

        @DisplayName("재고 수량이 주어지지 않으면, 상품 기본 정보만 수정하고 기존 재고를 유지한다.")
        @Test
        void keepsStockQuantity_whenStockQuantityIsNotProvided() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Long productId = createProduct(new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            ), adminHeaders()).getBody().data().id();
            ProductAdminV1Dto.UpdateProductRequest request = new ProductAdminV1Dto.UpdateProductRequest(
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L,
                null
            );

            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = updateProduct(productId, request, adminHeaders());

            // assert
            ProductAdminV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.name()).isEqualTo("아이폰 16 Pro Max"),
                () -> assertThat(data.description()).isEqualTo("더 큰 화면과 향상된 배터리를 제공하는 스마트폰"),
                () -> assertThat(data.price()).isEqualTo(1_900_000L),
                () -> assertThat(data.stockQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("상품 수정 후 공개 상세 조회 캐시를 무효화한다.")
        @Test
        void evictsPublicProductDetailCache_whenProductIsUpdated() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Long productId = createProduct(new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            ), adminHeaders()).getBody().data().id();
            getPublicProduct(productId);
            ProductAdminV1Dto.UpdateProductRequest request = new ProductAdminV1Dto.UpdateProductRequest(
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L,
                null
            );

            // act
            updateProduct(productId, request, adminHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getPublicProduct(productId);

            // assert
            ProductV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(productId),
                () -> assertThat(data.name()).isEqualTo("아이폰 16 Pro Max"),
                () -> assertThat(data.description()).isEqualTo("더 큰 화면과 향상된 배터리를 제공하는 스마트폰"),
                () -> assertThat(data.price()).isEqualTo(1_900_000L)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("어드민 헤더와 존재하는 상품 ID가 주어지면 200 OK를 반환하고, 이후 상세 조회에서 제외한다.")
        @Test
        void deletesProduct_whenAdminHeaderAndProductIdExist() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L,
                10
            );
            Long productId = createProduct(request, adminHeaders()).getBody().data().id();

            // act
            ResponseEntity<ApiResponse<Object>> deleteResponse = deleteProduct(productId, adminHeaders());
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> getResponse = getProduct(productId, adminHeaders());

            // assert
            assertAll(
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> createProduct(
        ProductAdminV1Dto.CreateProductRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCTS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> getProduct(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_DETAIL,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            productId
        );
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getPublicProduct(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PUBLIC_PRODUCT_DETAIL,
            HttpMethod.GET,
            null,
            responseType,
            productId
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> getProducts(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCTS,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> deleteProduct(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_DETAIL,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            responseType,
            productId
        );
    }

    private long likeSummaryCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select like_count from product_like_summary where product_id = ?",
            Long.class,
            productId
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> updateProduct(
        Long productId,
        ProductAdminV1Dto.UpdateProductRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_DETAIL,
            HttpMethod.PUT,
            new HttpEntity<>(request, headers),
            responseType,
            productId
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP);
        return headers;
    }
}
