package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.brand.BrandV1Dto;
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
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String CUSTOMER = "/api/v1/brands";
    private static final String ADMIN = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
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

    @DisplayName("대고객 GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("비로그인 사용자가 헤더 없이 단건을 조회할 수 있다. (US-01)")
        @Test
        void returnsOk_withoutHeader() {
            Brand saved = brandJpaRepository.save(Brand.create("브랜드A", "소개"));

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                    CUSTOMER + "/" + saved.getId(), HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("브랜드A");
        }

        @DisplayName("존재하지 않으면 404 NOT_FOUND. (AC-01-2)")
        @Test
        void returnsNotFound_whenMissing() {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                    CUSTOMER + "/99999", HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("어드민 POST /api-admin/v1/brands")
    @Nested
    class Register {

        @DisplayName("어드민 헤더가 있으면 200 과 브랜드 정보를 돌려준다. (US-10)")
        @Test
        void returnsOkWithBrand_whenAdmin() {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new BrandV1Dto.RegisterRequest("브랜드A", "소개"), adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().name()).isEqualTo("브랜드A");
        }

        @DisplayName("X-Loopers-Ldap 헤더가 누락되면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenAdminHeaderMissing() {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new BrandV1Dto.RegisterRequest("브랜드A", "소개"), empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("어드민 GET /api-admin/v1/brands")
    @Nested
    class GetBrands {

        @DisplayName("등록된 브랜드 목록을 페이징하여 돌려준다. (AC-13-1)")
        @Test
        void returnsPagedList() {
            brandJpaRepository.save(Brand.create("브랜드A", "소개"));
            brandJpaRepository.save(Brand.create("브랜드B", "소개"));

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.PageResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.GET, new HttpEntity<>(adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(2);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2L);
        }

        @DisplayName("어드민 헤더가 누락되면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.PageResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("어드민 PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class Modify {

        @DisplayName("어드민이 수정하면 변경 내용이 반영된다. (AC-11-1)")
        @Test
        void updatesBrand_whenAdmin() {
            Brand saved = brandJpaRepository.save(Brand.create("원본", "원본 설명"));

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + saved.getId(), HttpMethod.PUT,
                    new HttpEntity<>(new BrandV1Dto.ModifyRequest("변경", "변경 설명"), adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Brand reloaded = brandJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("변경");
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면 404 NOT_FOUND.")
        @Test
        void returnsNotFound_whenMissing() {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/99999", HttpMethod.PUT,
                    new HttpEntity<>(new BrandV1Dto.ModifyRequest("변경", "변경 설명"), adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("어드민 DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class Delete {

        @DisplayName("어드민이 삭제하면 브랜드와 소속 상품이 모두 논리 삭제된다. (AC-12-1)")
        @Test
        void cascadesDelete_whenAdmin() {
            Brand brand = brandJpaRepository.save(Brand.create("브랜드A", "소개"));
            Product product = productJpaRepository.save(
                    Product.create(brand.getId(), "상품1", Money.of(1_000L)));

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + brand.getId(), HttpMethod.DELETE, new HttpEntity<>(adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(brandJpaRepository.findById(brand.getId()).orElseThrow().getDeletedAt()).isNotNull();
            assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getDeletedAt()).isNotNull();
        }

        @DisplayName("어드민 헤더가 없으면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            Brand brand = brandJpaRepository.save(Brand.create("브랜드A", "소개"));

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + brand.getId(), HttpMethod.DELETE, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
