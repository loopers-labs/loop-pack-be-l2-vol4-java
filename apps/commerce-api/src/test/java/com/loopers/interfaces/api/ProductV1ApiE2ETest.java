package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String CUSTOMER = "/api/v1/products";
    private static final String ADMIN = "/api-admin/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final InventoryJpaRepository inventoryJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public ProductV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            InventoryJpaRepository inventoryJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.inventoryJpaRepository = inventoryJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static HttpHeaders adminHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private static HttpHeaders empty() {
        return new HttpHeaders();
    }

    private Long saveProduct(String name, long price, int stock) {
        Long productId = productJpaRepository.save(
                Product.create(brandId, name, Money.of(price))).getId();
        inventoryJpaRepository.save(Inventory.create(productId, stock));
        return productId;
    }

    @DisplayName("대고객 GET /api/v1/products")
    @Nested
    class GetList {

        @DisplayName("비로그인 사용자가 기본 정렬(최신순)로 목록을 조회할 수 있다. (US-02, AC-02-1)")
        @Test
        void returnsLatestByDefault() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long p2 = saveProduct("상품2", 2_000L, 10);

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = testRestTemplate.exchange(
                    CUSTOMER, HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content())
                    .extracting(ProductV1Dto.ListItemResponse::id)
                    .containsExactly(p2, p1);
        }

        @DisplayName("brandId 를 지정하면 해당 브랜드 상품만 조회된다. (AC-02-2)")
        @Test
        void filtersByBrand() {
            Long otherBrandId = brandJpaRepository.save(Brand.create("브랜드B", "소개")).getId();
            saveProduct("A상품", 1_000L, 10);
            Long bId = productJpaRepository.save(Product.create(otherBrandId, "B상품", Money.of(1_000L))).getId();
            inventoryJpaRepository.save(Inventory.create(bId, 10));

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = testRestTemplate.exchange(
                    CUSTOMER + "?brandId=" + brandId, HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content().get(0).brandId()).isEqualTo(brandId);
        }

        @DisplayName("price_asc 정렬 파라미터(소문자)가 동작한다. (AC-02-3)")
        @Test
        void sortsByPriceAsc() {
            saveProduct("비싼것", 3_000L, 10);
            saveProduct("싼것", 1_000L, 10);

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = testRestTemplate.exchange(
                    CUSTOMER + "?sort=price_asc", HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getBody().data().content())
                    .extracting(ProductV1Dto.ListItemResponse::price)
                    .containsExactly(1_000L, 3_000L);
        }
    }

    @DisplayName("대고객 GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품을 조회하면 상세를 돌려준다. (US-03, AC-03-1)")
        @Test
        void returnsDetail() {
            Long productId = saveProduct("상품1", 1_000L, 10);

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.DetailResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
                    CUSTOMER + "/" + productId, HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("상품1");
            assertThat(response.getBody().data().brandName()).isEqualTo("브랜드A");
        }

        @DisplayName("존재하지 않는 상품을 조회하면 404 NOT_FOUND. (AC-03-2)")
        @Test
        void returnsNotFound_whenMissing() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.DetailResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
                    CUSTOMER + "/99999", HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("어드민 POST /api-admin/v1/products")
    @Nested
    class Register {

        @DisplayName("어드민이 존재하는 브랜드에 상품을 등록하면 200 과 상품 정보를 돌려준다. (US-14, AC-14-1)")
        @Test
        void returnsOk_whenAdmin() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.CreatedResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.CreatedResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new ProductV1Dto.RegisterRequest(brandId, "상품1", 1_000L, 10), adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().name()).isEqualTo("상품1");
        }

        @DisplayName("존재하지 않는 브랜드에 등록하면 404 NOT_FOUND. (AC-14-2)")
        @Test
        void returnsNotFound_whenBrandMissing() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.CreatedResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.CreatedResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new ProductV1Dto.RegisterRequest(99999L, "상품1", 1_000L, 10), adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 누락되면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.CreatedResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.CreatedResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new ProductV1Dto.RegisterRequest(brandId, "상품1", 1_000L, 10), empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("어드민 PUT /api-admin/v1/products/{productId}")
    @Nested
    class Modify {

        @DisplayName("어드민이 수정하면 이름·가격·재고가 반영된다. (US-15, AC-15-3, AC-15-4)")
        @Test
        void updatesProduct_whenAdmin() {
            Long productId = saveProduct("원본", 1_000L, 10);

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + productId, HttpMethod.PUT,
                    new HttpEntity<>(new ProductV1Dto.ModifyRequest("변경", 5_000L, 3), adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Product reloaded = productJpaRepository.findById(productId).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("변경");
            assertThat(reloaded.getPrice().getAmount()).isEqualTo(5_000L);
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId).orElseThrow().getQuantity()).isEqualTo(3);
        }
    }

    @DisplayName("어드민 DELETE /api-admin/v1/products/{productId}")
    @Nested
    class Delete {

        @DisplayName("어드민이 삭제하면 논리 삭제되어 대고객 조회에서 제외된다. (US-16, AC-16-1)")
        @Test
        void softDeletes_whenAdmin() {
            Long productId = saveProduct("상품1", 1_000L, 10);

            ParameterizedTypeReference<ApiResponse<Void>> deleteType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(adminHeader()), deleteType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(productJpaRepository.findById(productId).orElseThrow().getDeletedAt()).isNotNull();

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.DetailResponse>> getType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> getResponse = testRestTemplate.exchange(
                    CUSTOMER + "/" + productId, HttpMethod.GET, new HttpEntity<>(empty()), getType);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 없으면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            Long productId = saveProduct("상품1", 1_000L, 10);

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
