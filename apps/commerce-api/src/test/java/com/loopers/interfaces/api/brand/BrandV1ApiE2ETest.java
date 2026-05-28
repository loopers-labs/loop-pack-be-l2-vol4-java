package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private static final String ENDPOINT_CUSTOMER = "/api/v1/brands";
    private static final String ENDPOINT_ADMIN = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    BrandV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandFacade brandFacade,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    private BrandInfo createBrand(String name, String description) {
        return brandFacade.createBrand(name, description);
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/brands/{brandId} — Customer
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 brandId로 조회하면 200과 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandInfo created = createBrand("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_CUSTOMER + "/" + created.id(),
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(created.id());
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
            assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드");
        }

        @DisplayName("존재하지 않는 brandId로 조회하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
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
    // POST /api-admin/v1/brands — Admin 등록
    // ─────────────────────────────────────────────

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 요청이면 200과 등록된 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenRequestIsValid() {
            // arrange
            BrandV1Dto.CreateBrandRequest request = new BrandV1Dto.CreateBrandRequest("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandAdminResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandAdminResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
            assertThat(response.getBody().data().createdAt()).isNotNull();
        }

        @DisplayName("중복된 name으로 등록하면 409를 반환한다.")
        @Test
        void returnsConflict_whenNameAlreadyExists() {
            // arrange
            createBrand("나이키", "스포츠 브랜드");
            BrandV1Dto.CreateBrandRequest request = new BrandV1Dto.CreateBrandRequest("나이키", "다른 설명");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("Admin 헤더 없이 요청하면 403을 반환한다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandV1Dto.CreateBrandRequest request = new BrandV1Dto.CreateBrandRequest("나이키", "스포츠 브랜드");

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
    // GET /api-admin/v1/brands — Admin 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetBrands {

        @DisplayName("등록된 브랜드 수만큼 목록을 반환한다.")
        @Test
        void returnsBrandPage_withAllBrands() {
            // arrange
            createBrand("나이키", "스포츠 브랜드");
            createBrand("아디다스", "독일 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Page<BrandV1Dto.BrandAdminResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Page<BrandV1Dto.BrandAdminResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().getTotalElements()).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/brands/{brandId} — Admin 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrandAdmin {

        @DisplayName("존재하는 brandId로 조회하면 200과 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandInfo created = createBrand("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandAdminResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandAdminResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + created.id(),
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
            assertThat(response.getBody().data().createdAt()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────
    // PUT /api-admin/v1/brands/{brandId} — Admin 수정
    // ─────────────────────────────────────────────

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("유효한 요청이면 200과 수정된 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenRequestIsValid() {
            // arrange
            BrandInfo created = createBrand("나이키", "스포츠 브랜드");
            BrandV1Dto.UpdateBrandRequest request = new BrandV1Dto.UpdateBrandRequest("나이키 코리아", "한국 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandAdminResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandAdminResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + created.id(),
                            HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("나이키 코리아");
        }
    }

    // ─────────────────────────────────────────────
    // DELETE /api-admin/v1/brands/{brandId} — Admin 삭제
    // ─────────────────────────────────────────────

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 brandId로 삭제하면 200을 반환한다.")
        @Test
        void returnsOk_whenBrandExists() {
            // arrange
            BrandInfo created = createBrand("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + created.id(),
                            HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
