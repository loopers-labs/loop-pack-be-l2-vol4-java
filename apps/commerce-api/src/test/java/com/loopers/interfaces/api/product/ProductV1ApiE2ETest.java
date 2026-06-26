package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.user.UserApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private static final String ENDPOINT_CUSTOMER = "/api/v1/products";
    private static final String ENDPOINT_ADMIN = "/api-admin/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandApplicationService brandApplicationService;
    private final ProductApplicationService productApplicationService;
    private final LikeApplicationService likeApplicationService;
    private final UserApplicationService userApplicationService;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    ProductV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandApplicationService brandApplicationService,
            ProductApplicationService productApplicationService,
            LikeApplicationService likeApplicationService,
            UserApplicationService userApplicationService,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandApplicationService = brandApplicationService;
        this.productApplicationService = productApplicationService;
        this.likeApplicationService = likeApplicationService;
        this.userApplicationService = userApplicationService;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        // 상품 목록 page-0 캐시(Redis)가 테스트 간 공유되지 않도록 정리
        redisCleanUp.truncateAll();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    private BrandInfo createBrand(String name) {
        return brandApplicationService.createBrand(name, name + " 설명");
    }

    private ProductInfo createProduct(String brandId, String name, Long price, Integer quantity) {
        return productApplicationService.createProduct(brandId, name, name + " 설명", price, quantity);
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/products — Customer 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("sort 미전달 시 최신 등록순(latest)으로 반환한다.")
        @Test
        void returnsProductsInLatestOrder_whenSortIsNotProvided() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo first = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            ProductInfo second = createProduct(brand.id(), "에어포스", 120_000L, 5);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<ProductV1Dto.PlpResponse> content = response.getBody().data().content();
            assertThat(content).hasSize(2);
            assertThat(content.get(0).id()).isEqualTo(second.id());
            assertThat(content.get(1).id()).isEqualTo(first.id());
        }

        @DisplayName("sort=price_asc 이면 가격 오름차순으로 반환한다.")
        @Test
        void returnsProductsInPriceAscOrder_whenSortIsPriceAsc() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo cheap = createProduct(brand.id(), "에어맥스", 80_000L, 10);
            ProductInfo expensive = createProduct(brand.id(), "에어포스", 150_000L, 5);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?sort=price_asc&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<ProductV1Dto.PlpResponse> content = response.getBody().data().content();
            assertThat(content.get(0).id()).isEqualTo(cheap.id());
            assertThat(content.get(1).id()).isEqualTo(expensive.id());
        }

        @DisplayName("sort=price_desc 이면 가격 내림차순으로 반환한다.")
        @Test
        void returnsProductsInPriceDescOrder_whenSortIsPriceDesc() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo cheap = createProduct(brand.id(), "에어맥스", 80_000L, 10);
            ProductInfo expensive = createProduct(brand.id(), "에어포스", 150_000L, 5);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?sort=price_desc&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<ProductV1Dto.PlpResponse> content = response.getBody().data().content();
            assertThat(content.get(0).id()).isEqualTo(expensive.id());
            assertThat(content.get(1).id()).isEqualTo(cheap.id());
        }

        @DisplayName("sort=like_desc 이면 좋아요 수 내림차순으로 반환한다.")
        @Test
        void returnsProductsInLikeDescOrder_whenSortIsLikeDesc() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo noLike = createProduct(brand.id(), "에어맥스", 80_000L, 10);
            ProductInfo hasLike = createProduct(brand.id(), "에어포스", 150_000L, 5);

            String userId = userApplicationService.signup("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 1, 1), "test@test.com").id();
            likeApplicationService.addLike(userId, hasLike.id());

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?sort=like_desc&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<ProductV1Dto.PlpResponse> content = response.getBody().data().content();
            assertThat(content.get(0).id()).isEqualTo(hasLike.id());
            assertThat(content.get(1).id()).isEqualTo(noLike.id());
        }

        @DisplayName("sort=like_asc 이면 좋아요 수 오름차순으로 반환한다.")
        @Test
        void returnsProductsInLikeAscOrder_whenSortIsLikeAsc() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo noLike = createProduct(brand.id(), "에어맥스", 80_000L, 10);
            ProductInfo hasLike = createProduct(brand.id(), "에어포스", 150_000L, 5);

            String userId = userApplicationService.signup("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 1, 1), "test@test.com").id();
            likeApplicationService.addLike(userId, hasLike.id());

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?sort=like_asc&page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<ProductV1Dto.PlpResponse> content = response.getBody().data().content();
            assertThat(content.get(0).id()).isEqualTo(noLike.id());
            assertThat(content.get(1).id()).isEqualTo(hasLike.id());
        }

        @DisplayName("알 수 없는 sort 값이면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenSortIsUnknown() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?sort=invalid_sort",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("Customer PLP 응답에 quantity와 description이 포함되지 않는다.")
        @Test
        void plpResponse_doesNotContainQuantityAndDescription() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            createProduct(brand.id(), "에어맥스", 100_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.PlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "?page=0&size=20",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ProductV1Dto.PlpResponse item = response.getBody().data().content().get(0);
            assertThat(item.id()).isNotNull();
            assertThat(item.brandId()).isNotNull();
            assertThat(item.brandName()).isEqualTo("나이키");
            assertThat(item.name()).isEqualTo("에어맥스");
            assertThat(item.price()).isEqualTo(100_000L);
            assertThat(item.likeCount()).isEqualTo(0L);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/products/{productId} — Customer 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 productId로 조회하면 200과 PDP 정보를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo created = createProduct(brand.id(), "에어맥스", 100_000L, 5);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.PdpResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.PdpResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "/" + created.id(),
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ProductV1Dto.PdpResponse data = response.getBody().data();
            assertThat(data.id()).isEqualTo(created.id());
            assertThat(data.brandId()).isEqualTo(brand.id());
            assertThat(data.brandName()).isEqualTo("나이키");
            assertThat(data.name()).isEqualTo("에어맥스");
            assertThat(data.price()).isEqualTo(100_000L);
            assertThat(data.likeCount()).isEqualTo(0L);
            assertThat(data.quantity()).isEqualTo(5);
            assertThat(data.description()).isEqualTo("에어맥스 설명");
        }

        @DisplayName("존재하지 않는 productId로 조회하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "/999",
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // POST /api-admin/v1/products — Admin 등록
    // ─────────────────────────────────────────────

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 요청이면 201과 생성된 상품 ID를 반환한다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductV1Dto.CreateProductRequest request =
                    new ProductV1Dto.CreateProductRequest(brand.id(), "에어맥스", "최고의 운동화", 100_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.CreateProductResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.CreateProductResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().id()).isNotNull();
        }

        @DisplayName("존재하지 않는 brandId로 등록하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            ProductV1Dto.CreateProductRequest request =
                    new ProductV1Dto.CreateProductRequest("999", "에어맥스", "최고의 운동화", 100_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("Admin 헤더 없이 요청하면 403을 반환한다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            ProductV1Dto.CreateProductRequest request =
                    new ProductV1Dto.CreateProductRequest("1", "에어맥스", "최고의 운동화", 100_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/products — Admin 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetProductsAdmin {

        @DisplayName("등록된 상품 수만큼 목록을 반환한다.")
        @Test
        void returnsProductPage_withAllProducts() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            createProduct(brand.id(), "에어맥스", 100_000L, 10);
            createProduct(brand.id(), "에어포스", 120_000L, 5);

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<ProductV1Dto.AdminPlpResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<ProductV1Dto.AdminPlpResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
            ProductV1Dto.AdminPlpResponse item = response.getBody().data().content().get(0);
            assertThat(item.quantity()).isNotNull();
            assertThat(item.createdAt()).isNotNull();
            assertThat(item.updatedAt()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/products/{productId} — Admin 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProductAdmin {

        @DisplayName("존재하는 productId로 조회하면 200과 AdminPDP 정보를 반환한다.")
        @Test
        void returnsAdminProduct_whenProductExists() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo created = createProduct(brand.id(), "에어맥스", 100_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.AdminPdpResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.AdminPdpResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + created.id(),
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ProductV1Dto.AdminPdpResponse data = response.getBody().data();
            assertThat(data.id()).isEqualTo(created.id());
            assertThat(data.name()).isEqualTo("에어맥스");
            assertThat(data.quantity()).isEqualTo(10);
            assertThat(data.description()).isEqualTo("에어맥스 설명");
            assertThat(data.createdAt()).isNotNull();
            assertThat(data.updatedAt()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────
    // PUT /api-admin/v1/products/{productId} — Admin 수정
    // ─────────────────────────────────────────────

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 요청이면 204 No Content를 반환한다.")
        @Test
        void returnsNoContent_whenRequestIsValid() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo created = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            ProductV1Dto.UpdateProductRequest request =
                    new ProductV1Dto.UpdateProductRequest("에어맥스 V2", "업데이트된 설명", 120_000L, 20);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + created.id(),
                            HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    // ─────────────────────────────────────────────
    // DELETE /api-admin/v1/products/{productId} — Admin 삭제
    // ─────────────────────────────────────────────

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 productId로 삭제하면 204 No Content를 반환한다.")
        @Test
        void returnsNoContent_whenProductExists() {
            // arrange
            BrandInfo brand = createBrand("나이키");
            ProductInfo created = createProduct(brand.id(), "에어맥스", 100_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + created.id(),
                            HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @DisplayName("존재하지 않는 productId로 삭제하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/999",
                            HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
