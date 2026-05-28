package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
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
class BrandV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/brands";
    private static final String ADMIN_BASE_URL = "/api-admin/v1/brands";
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
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        return productRepository.save(new ProductModel(brandId, name, price));
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

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID로 조회하면 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandResponse_whenBrandExists() {
            // given
            BrandModel brand = saveBrand("Nike");

            // when
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(BASE_URL + "/" + brand.getId(), HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 조회하면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(BASE_URL + "/999", HttpMethod.GET, null, Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetAllBrands {

        @DisplayName("브랜드 목록이 있으면 페이지네이션된 목록을 반환한다.")
        @Test
        void returnsPagedBrands_whenBrandsExist() {
            // given
            saveBrand("Nike");
            saveBrand("Adidas");

            // when
            String url = ADMIN_BASE_URL + "?page=0&size=20";
            ParameterizedTypeReference<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("LDAP 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL, HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 LDAP 값이면 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL, HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetAdminBrand {

        @DisplayName("존재하는 브랜드 ID로 조회하면 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandResponse_whenBrandExists() {
            // given
            BrandModel brand = saveBrand("Nike");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/" + brand.getId(), HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/999", HttpMethod.GET, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 요청이면 생성된 브랜드 정보를 반환하고 DB에 저장된다.")
        @Test
        void returnsCreatedBrand_andPersistedInDatabase() {
            // given
            BrandAdminV1Dto.CreateBrandRequest request = new BrandAdminV1Dto.CreateBrandRequest("나이키");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ADMIN_BASE_URL, HttpMethod.POST, adminJsonEntity(request), responseType);

            // then
            BrandModel persisted = brandRepository.findById(response.getBody().data().id()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(persisted.getName()).isEqualTo("나이키")
            );
        }

        @DisplayName("잘못된 LDAP 값이면 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenLdapIsInvalid() {
            // given
            BrandAdminV1Dto.CreateBrandRequest request = new BrandAdminV1Dto.CreateBrandRequest("나이키");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL, HttpMethod.POST, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("브랜드명이 null이면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsNull() {
            // given
            BrandAdminV1Dto.CreateBrandRequest request = new BrandAdminV1Dto.CreateBrandRequest(null);

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL, HttpMethod.POST, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 빈 문자열이면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // given
            BrandAdminV1Dto.CreateBrandRequest request = new BrandAdminV1Dto.CreateBrandRequest("   ");

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL, HttpMethod.POST, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드의 이름을 수정하면 수정된 정보를 반환하고 DB에 반영된다.")
        @Test
        void returnsUpdatedBrand_andPersistedInDatabase() {
            // given
            BrandModel brand = saveBrand("Nike");
            BrandAdminV1Dto.UpdateBrandRequest request = new BrandAdminV1Dto.UpdateBrandRequest("아디다스");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/" + brand.getId(), HttpMethod.PUT, adminJsonEntity(request), responseType);

            // then
            BrandModel persisted = brandRepository.findById(brand.getId()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                () -> assertThat(persisted.getName()).isEqualTo("아디다스")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // given
            BrandAdminV1Dto.UpdateBrandRequest request = new BrandAdminV1Dto.UpdateBrandRequest("아디다스");

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/999", HttpMethod.PUT, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("브랜드명이 null이면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsNull() {
            // given
            BrandModel brand = saveBrand("Nike");
            BrandAdminV1Dto.UpdateBrandRequest request = new BrandAdminV1Dto.UpdateBrandRequest(null);

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/" + brand.getId(), HttpMethod.PUT, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 빈 문자열이면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // given
            BrandModel brand = saveBrand("Nike");
            BrandAdminV1Dto.UpdateBrandRequest request = new BrandAdminV1Dto.UpdateBrandRequest("   ");

            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/" + brand.getId(), HttpMethod.PUT, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 브랜드와 관련 상품을 삭제하면 성공 응답을 반환하고 브랜드와 상품이 조회되지 않는다.")
        @Test
        void returnsSuccess_andBrandAndProductAreDeleted() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "에어맥스", BigDecimal.valueOf(150000));
            saveStock(product.getId(), 10L);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/" + brand.getId(), HttpMethod.DELETE, adminHeaderEntity(), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(brandRepository.findById(brand.getId())).isEmpty(),
                () -> assertThat(productRepository.find(product.getId())).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ADMIN_BASE_URL + "/999", HttpMethod.DELETE, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
