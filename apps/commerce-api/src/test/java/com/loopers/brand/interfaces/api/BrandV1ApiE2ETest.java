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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> create(BrandV1Dto.CreateRequest request) {
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type);
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> getById(Long brandId) {
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.GET, null, type);
    }

    @DisplayName("POST /api/v1/brands")
    @Nested
    class Create {

        @Test
        @DisplayName("유효한 요청으로 등록하면 200 과 등록된 브랜드 정보를 반환한다")
        void givenValidRequest_whenCreate_thenReturnsBrand() {
            BrandV1Dto.CreateRequest request = new BrandV1Dto.CreateRequest("루퍼스", "트렌디한 라이프스타일");

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = create(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("루퍼스"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("트렌디한 라이프스타일")
            );
        }

        @Test
        @DisplayName("description 없이 등록해도 200 을 받는다")
        void givenRequestWithoutDescription_whenCreate_thenReturnsBrand() {
            BrandV1Dto.CreateRequest request = new BrandV1Dto.CreateRequest("루퍼스", null);

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = create(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().description()).isNull()
            );
        }

        @Test
        @DisplayName("이름이 비어있으면 400 BAD_REQUEST 응답을 받는다")
        void givenBlankName_whenCreate_thenThrowsBadRequest() {
            BrandV1Dto.CreateRequest request = new BrandV1Dto.CreateRequest("  ", "설명");

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("존재하는 brandId 로 조회하면 200 과 브랜드 정보를 반환한다")
        void givenExistingBrandId_whenGet_thenReturnsBrand() {
            Long brandId = create(new BrandV1Dto.CreateRequest("루퍼스", "설명")).getBody().data().id();

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = getById(brandId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("루퍼스")
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 로 조회하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingBrandId_whenGet_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = getById(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/brands")
    @Nested
    class GetAll {

        @Test
        @DisplayName("등록된 브랜드 목록을 200 으로 반환한다")
        void givenSavedBrands_whenGetAll_thenReturnsAllBrands() {
            create(new BrandV1Dto.CreateRequest("A", "설명"));
            create(new BrandV1Dto.CreateRequest("B", "설명"));

            ParameterizedTypeReference<ApiResponse<List<BrandV1Dto.BrandResponse>>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<BrandV1Dto.BrandResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(BrandV1Dto.BrandResponse::name)
                            .containsExactlyInAnyOrder("A", "B")
            );
        }
    }

    @DisplayName("PUT /api/v1/brands/{brandId}")
    @Nested
    class Update {

        private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> update(Long brandId, BrandV1Dto.UpdateRequest request) {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.PUT, new HttpEntity<>(request), type);
        }

        @Test
        @DisplayName("유효한 요청으로 수정하면 200 과 변경된 브랜드 정보를 반환한다")
        void givenValidRequest_whenUpdate_thenReturnsUpdatedBrand() {
            Long brandId = create(new BrandV1Dto.CreateRequest("루퍼스", "설명")).getBody().data().id();

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    update(brandId, new BrandV1Dto.UpdateRequest("뉴루퍼스", "새 설명"));

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("뉴루퍼스"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("새 설명")
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 를 수정하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingBrandId_whenUpdate_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    update(999L, new BrandV1Dto.UpdateRequest("뉴루퍼스", "설명"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("이름이 비어있으면 400 BAD_REQUEST 응답을 받는다")
        void givenBlankName_whenUpdate_thenThrowsBadRequest() {
            Long brandId = create(new BrandV1Dto.CreateRequest("루퍼스", "설명")).getBody().data().id();

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    update(brandId, new BrandV1Dto.UpdateRequest("  ", "설명"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/brands/{brandId}")
    @Nested
    class Delete {

        private ResponseEntity<ApiResponse<Void>> delete(Long brandId) {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.DELETE, null, type);
        }

        @Test
        @DisplayName("존재하는 brandId 를 삭제하면 200 응답 후 더 이상 조회되지 않는다")
        void givenExistingBrandId_whenDelete_thenBrandIsNotFoundAfterwards() {
            Long brandId = create(new BrandV1Dto.CreateRequest("루퍼스", "설명")).getBody().data().id();

            ResponseEntity<ApiResponse<Void>> deleteResponse = delete(brandId);

            assertAll(
                    () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getById(brandId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 를 삭제하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingBrandId_whenDelete_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<Void>> response = delete(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
