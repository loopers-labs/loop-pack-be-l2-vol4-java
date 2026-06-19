package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/products";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, price));
        productStatsRepository.save(new ProductStatsModel(product));
        return product;
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    private HttpEntity<Void> adminHeaderEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LDAP, ADMIN_LDAP);
        return new HttpEntity<>(null, headers);
    }

    private <T> HttpEntity<T> adminJsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AuthHeaders.LDAP, ADMIN_LDAP);
        return new HttpEntity<>(body, headers);
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("브랜드 ID로 상품 목록을 조회하면 페이지네이션된 상품 목록을 반환한다.")
        @Test
        void returnsPagedProducts_whenBrandHasProducts() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel productA = saveProduct(brand.getId(), "에어맥스", BigDecimal.valueOf(150000));
            ProductModel productB = saveProduct(brand.getId(), "조던", BigDecimal.valueOf(200000));
            saveStock(productA.getId(), 10L);
            saveStock(productB.getId(), 5L);

            // when
            String url = BASE_URL + "?brandId=" + brand.getId() + "&page=0&size=20";
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("LDAP 헤더가 없으면 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenLdapHeaderIsMissing() {
            // given
            BrandModel brand = saveBrand("Nike");

            // when
            String url = BASE_URL + "?brandId=" + brand.getId();
            ResponseEntity<Void> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 LDAP 값이면 403 Forbidden 응답을 반환한다.")
        @Test
        void returnsForbidden_whenLdapIsInvalid() {
            // given
            BrandModel brand = saveBrand("Nike");
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            String url = BASE_URL + "?brandId=" + brand.getId();
            ResponseEntity<Void> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID로 조회하면 재고를 포함한 상품 상세 정보를 반환한다.")
        @Test
        void returnsProductDetail_whenProductExists() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "에어맥스", BigDecimal.valueOf(150000));
            saveStock(product.getId(), 10L);

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductDetailResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(BASE_URL + "/" + product.getId(), HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(10L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 조회하면 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(BASE_URL + "/999", HttpMethod.GET, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 브랜드 ID와 상품 정보로 상품을 등록하면 생성된 상품 상세 정보를 반환한다.")
        @Test
        void returnsCreatedProduct_whenRequestIsValid() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                brand.getId(), "에어맥스", BigDecimal.valueOf(150000), 10L
            );

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductDetailResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(BASE_URL, HttpMethod.POST, adminJsonEntity(request), responseType);

            // then
            ProductModel persisted = productRepository.find(response.getBody().data().id()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(10L),
                () -> assertThat(persisted.getName()).isEqualTo("에어맥스"),
                () -> assertThat(persisted.getBrandId()).isEqualTo(brand.getId())
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 상품 등록 시 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // given
            ProductAdminV1Dto.CreateProductRequest request = new ProductAdminV1Dto.CreateProductRequest(
                999L, "에어맥스", BigDecimal.valueOf(150000), 10L
            );

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(BASE_URL, HttpMethod.POST, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("상품 정보를 수정하면 수정된 상품 상세 정보를 반환하며 브랜드는 변경되지 않는다.")
        @Test
        void returnsUpdatedProduct_andBrandIdIsNotChanged() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "기존 상품", BigDecimal.valueOf(100000));
            saveStock(product.getId(), 10L);
            ProductAdminV1Dto.UpdateProductRequest request = new ProductAdminV1Dto.UpdateProductRequest(
                "수정 상품", BigDecimal.valueOf(200000), 20L
            );

            // when
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductDetailResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(BASE_URL + "/" + product.getId(), HttpMethod.PUT, adminJsonEntity(request), responseType);

            // then
            ProductModel persisted = productRepository.find(product.getId()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정 상품"),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(BigDecimal.valueOf(200000)),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(20L),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId()),
                () -> assertThat(persisted.getBrandId()).isEqualTo(brand.getId()),
                () -> assertThat(persisted.getName()).isEqualTo("수정 상품")
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 수정 요청 시 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // given
            ProductAdminV1Dto.UpdateProductRequest request = new ProductAdminV1Dto.UpdateProductRequest(
                "상품", BigDecimal.valueOf(100000), 5L
            );

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(BASE_URL + "/999", HttpMethod.PUT, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품을 삭제하면 성공 응답을 반환하고 상품이 조회되지 않는다.")
        @Test
        void returnsSuccess_andProductIsDeleted() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "상품", BigDecimal.valueOf(100000));
            saveStock(product.getId(), 10L);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(BASE_URL + "/" + product.getId(), HttpMethod.DELETE, adminHeaderEntity(), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(productRepository.find(product.getId())).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 삭제 요청 시 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(BASE_URL + "/999", HttpMethod.DELETE, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
