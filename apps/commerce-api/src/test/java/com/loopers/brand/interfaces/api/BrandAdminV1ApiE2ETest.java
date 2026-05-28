package com.loopers.brand.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
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

    private static final String ENDPOINT = "/api/v1/admin/brands";
    private static final String ADMIN_HEADER = "X-Loopers-Admin-Id";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandAdminV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ADMIN_HEADER, "admin-1");
        return headers;
    }

    private ResponseEntity<ApiResponse<BrandV1Response.Detail>> createAsAdmin(BrandAdminV1Request.Create request) {
        ParameterizedTypeReference<ApiResponse<BrandV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type);
    }

    @DisplayName("POST /api/v1/admin/brands")
    @Nested
    class Create {

        @Test
        @DisplayName("admin 헤더와 유효한 요청으로 등록하면 200 과 등록된 브랜드(로고 포함)를 반환한다")
        void givenAdminAndValidRequest_whenCreate_thenReturnsBrand() {
            BrandAdminV1Request.Create request =
                    new BrandAdminV1Request.Create("루퍼스", "트렌디한 라이프스타일", "https://cdn.loopers.com/l.png");

            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response = createAsAdmin(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("루퍼스"),
                    () -> assertThat(response.getBody().data().logoUrl()).isEqualTo("https://cdn.loopers.com/l.png")
            );
        }

        @Test
        @DisplayName("admin 헤더 없이 등록하면 401 UNAUTHORIZED 응답을 받는다")
        void givenNoAdminHeader_whenCreate_thenUnauthorized() {
            BrandAdminV1Request.Create request = new BrandAdminV1Request.Create("루퍼스", "설명", null);
            ParameterizedTypeReference<ApiResponse<BrandV1Response.Detail>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("이미 존재하는 브랜드명으로 등록하면 409 CONFLICT 응답을 받는다")
        void givenDuplicateName_whenCreate_thenConflict() {
            createAsAdmin(new BrandAdminV1Request.Create("루퍼스", "설명", null));

            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response =
                    createAsAdmin(new BrandAdminV1Request.Create("루퍼스", "다른 설명", null));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("이름이 비어있으면 400 BAD_REQUEST 응답을 받는다")
        void givenBlankName_whenCreate_thenBadRequest() {
            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response =
                    createAsAdmin(new BrandAdminV1Request.Create("  ", "설명", null));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PUT /api/v1/admin/brands/{brandId}")
    @Nested
    class Update {

        private ResponseEntity<ApiResponse<BrandV1Response.Detail>> update(Long brandId, BrandAdminV1Request.Update request) {
            ParameterizedTypeReference<ApiResponse<BrandV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), type);
        }

        @Test
        @DisplayName("admin 헤더와 유효한 요청으로 수정하면 200 과 변경된 정보를 반환한다")
        void givenAdminAndValidRequest_whenUpdate_thenReturnsUpdatedBrand() {
            Long brandId = createAsAdmin(new BrandAdminV1Request.Create("루퍼스", "설명", null)).getBody().data().id();

            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response =
                    update(brandId, new BrandAdminV1Request.Update("뉴루퍼스", "새 설명", "https://cdn.loopers.com/new.png"));

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("뉴루퍼스"),
                    () -> assertThat(response.getBody().data().logoUrl()).isEqualTo("https://cdn.loopers.com/new.png")
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 를 수정하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingBrandId_whenUpdate_thenNotFound() {
            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response =
                    update(999L, new BrandAdminV1Request.Update("뉴루퍼스", "설명", null));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/admin/brands/{brandId}")
    @Nested
    class Delete {

        private ResponseEntity<ApiResponse<Void>> delete(Long brandId) {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), type);
        }

        @Test
        @DisplayName("admin 헤더로 삭제하면 200 응답 후 더 이상 조회되지 않는다")
        void givenExistingBrandId_whenDelete_thenNotFoundAfterwards() {
            Long brandId = createAsAdmin(new BrandAdminV1Request.Create("루퍼스", "설명", null)).getBody().data().id();

            ResponseEntity<ApiResponse<Void>> deleteResponse = delete(brandId);

            ParameterizedTypeReference<ApiResponse<BrandV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Response.Detail>> getResponse =
                    testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);

            assertAll(
                    () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @Test
        @DisplayName("admin 헤더 없이 삭제하면 401 UNAUTHORIZED 응답을 받는다")
        void givenNoAdminHeader_whenDelete_thenUnauthorized() {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/1", HttpMethod.DELETE, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
