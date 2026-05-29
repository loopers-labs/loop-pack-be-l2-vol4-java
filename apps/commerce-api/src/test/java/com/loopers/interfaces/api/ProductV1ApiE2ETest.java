package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ADMIN_BRAND_URL   = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_URL = "/api-admin/v1/products";
    private static final String CUSTOMER_URL      = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private UUID createBrand() {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ADMIN_BRAND_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private UUID createProduct(UUID brandId) {
        return createProductWithPrice(brandId, ProductFixture.PRICE);
    }

    private UUID createProductWithPrice(UUID brandId, long price) {
        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
            ADMIN_PRODUCT_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, price, ProductFixture.INITIAL_QUANTITY
            ), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private void registerUser() {
        testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
        );
    }

    @DisplayName("GET /api/v1/products/{id}")
    @Nested
    class GetActive {

        @DisplayName("활성 상품 조회 시, 200 + stockStatus 포함 응답을 반환한다.")
        @Test
        void returnsProduct_whenActive() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "/" + productId, HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().stockStatus()).isEqualTo("IN_STOCK"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo(BrandFixture.NAME)
            );
        }

        @DisplayName("삭제된 상품 조회 시, 404를 반환한다.")
        @Test
        void throwsNotFound_whenDeleted() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);
            testRestTemplate.exchange(ADMIN_PRODUCT_URL + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "/" + productId, HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 ID 조회 시, 404를 반환한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetActiveList {

        @DisplayName("고객 목록 조회 시, 활성 상품만 반환된다.")
        @Test
        void returnsOnlyActiveProducts_whenGetList() {
            // arrange
            UUID brandId = createBrand();
            createProduct(brandId);
            createProduct(brandId);
            UUID deletedId = createProduct(brandId);
            testRestTemplate.exchange(ADMIN_PRODUCT_URL + "/" + deletedId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "?page=0&size=10", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert — 삭제 1건 제외 → 2건만
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(2)
            );
        }

        @DisplayName("brandId 필터로 조회 시, 해당 브랜드 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdProvided() {
            // arrange
            UUID brandId1 = createBrand();
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
                ADMIN_BRAND_URL, HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateRequest("다른브랜드", "설명"), adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID brandId2 = brandResp.getBody().data().id();

            createProduct(brandId1);
            createProduct(brandId1);
            createProduct(brandId2);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "?brandId=" + brandId1 + "&page=0&size=10", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert — brandId1 상품 2건만
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(2)
            );
        }

        @DisplayName("sort=latest 로 조회 시, 200 + 목록을 반환한다.")
        @Test
        void returnsList_whenSortLatest() {
            // arrange
            UUID brandId = createBrand();
            createProduct(brandId);
            createProduct(brandId);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "?sort=latest&page=0&size=10", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(2)
            );
        }

        @DisplayName("sort=price_asc 로 조회 시, 가격 오름차순으로 정렬된다.")
        @Test
        void returnsSortedByPriceAsc() {
            // arrange
            UUID brandId = createBrand();
            createProductWithPrice(brandId, 50_000L);
            createProductWithPrice(brandId, 10_000L);
            createProductWithPrice(brandId, 30_000L);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "?sort=price_asc&page=0&size=10", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert — 가격 오름차순: 10000, 30000, 50000
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent().get(0).price()).isEqualTo(10_000L),
                () -> assertThat(response.getBody().data().getContent().get(2).price()).isEqualTo(50_000L)
            );
        }

        @DisplayName("sort=likes_desc 로 조회 시, 좋아요 내림차순으로 정렬된다.")
        @Test
        void returnsSortedByLikesDesc() {
            // arrange
            UUID brandId = createBrand();
            createProduct(brandId);
            UUID likedProductId = createProduct(brandId);

            registerUser();
            testRestTemplate.exchange(
                "/api/v1/products/" + likedProductId + "/likes", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                CUSTOMER_URL + "?sort=likes_desc&page=0&size=10", HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {}
            );

            // assert — 좋아요 1건 상품이 첫 번째
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent().get(0).id()).isEqualTo(likedProductId),
                () -> assertThat(response.getBody().data().getContent().get(0).likeCount()).isEqualTo(1L)
            );
        }
    }
}
