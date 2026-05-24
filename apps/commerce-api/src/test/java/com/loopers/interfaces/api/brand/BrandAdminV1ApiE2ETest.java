package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String ENDPOINT_BRANDS = "/api-admin/v1/brands";
    private static final String ENDPOINT_BRAND_DETAIL = "/api-admin/v1/brands/{brandId}";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    BrandAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("어드민 헤더와 유효한 브랜드 정보가 주어지면, 201 CREATED 와 생성된 브랜드 정보를 반환한다.")
        @Test
        void returnsCreatedBrand_whenAdminHeaderAndValidRequestAreProvided() {
            // arrange
            BrandAdminV1Dto.CreateBrandRequest request = new BrandAdminV1Dto.CreateBrandRequest(
                "애플",
                "기술과 디자인으로 일상을 새롭게 만드는 브랜드"
            );

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = createBrand(request, adminHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("애플"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("기술과 디자인으로 일상을 새롭게 만드는 브랜드")
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // arrange
            BrandAdminV1Dto.CreateBrandRequest request = new BrandAdminV1Dto.CreateBrandRequest(
                "애플",
                "기술과 디자인으로 일상을 새롭게 만드는 브랜드"
            );

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = createBrand(request, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("어드민 헤더와 존재하는 브랜드 ID가 주어지면, 200 OK 와 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenAdminHeaderAndBrandIdExist() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = getBrand(saved.getId(), adminHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("애플"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("기술과 디자인으로 일상을 새롭게 만드는 브랜드")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetBrands {

        @DisplayName("어드민 헤더가 주어지면, 200 OK 와 기본 페이지 조건의 브랜드 목록을 반환한다.")
        @Test
        void returnsBrandPage_whenAdminHeaderIsProvided() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response = getBrands(adminHeaders());

            // assert
            PageResponse<BrandV1Dto.BrandResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content()).hasSize(1),
                () -> assertThat(data.content().getFirst().id()).isEqualTo(saved.getId()),
                () -> assertThat(data.totalElements()).isEqualTo(1),
                () -> assertThat(data.totalPages()).isEqualTo(1),
                () -> assertThat(data.number()).isZero(),
                () -> assertThat(data.size()).isEqualTo(20),
                () -> assertThat(data.first()).isTrue(),
                () -> assertThat(data.last()).isTrue()
            );
        }

        @DisplayName("브랜드 목록을 조회하면, 안정적인 기본 순서로 반환한다.")
        @Test
        void returnsBrandPageInDefaultOrder_whenBrandsExist() {
            // arrange
            Brand oldBrand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Brand latestBrand = brandService.createBrand("애플 스토어", "사용자 경험과 서비스를 함께 제공하는 브랜드");

            // act
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response = getBrands(adminHeaders());

            // assert
            PageResponse<BrandV1Dto.BrandResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content()).hasSize(2),
                () -> assertThat(data.content().get(0).id()).isEqualTo(latestBrand.getId()),
                () -> assertThat(data.content().get(1).id()).isEqualTo(oldBrand.getId())
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("어드민 헤더와 수정할 브랜드 정보가 주어지면, 200 OK 와 수정된 브랜드 정보를 반환한다.")
        @Test
        void returnsUpdatedBrand_whenAdminHeaderAndValidRequestAreProvided() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            BrandAdminV1Dto.UpdateBrandRequest request = new BrandAdminV1Dto.UpdateBrandRequest(
                "애플 스토어",
                "사용자 경험과 서비스를 함께 제공하는 브랜드"
            );

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = updateBrand(saved.getId(), request, adminHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("애플 스토어"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("사용자 경험과 서비스를 함께 제공하는 브랜드")
            );
        }
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> createBrand(
        BrandAdminV1Dto.CreateBrandRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_BRANDS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> getBrand(Long brandId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_BRAND_DETAIL,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            brandId
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> getBrands(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_BRANDS,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> updateBrand(
        Long brandId,
        BrandAdminV1Dto.UpdateBrandRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_BRAND_DETAIL,
            HttpMethod.PUT,
            new HttpEntity<>(request, headers),
            responseType,
            brandId
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP);
        return headers;
    }
}
