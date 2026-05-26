package com.loopers.interfaces.api;

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
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String BRANDS_PATH = "/api/v1/brands";

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

    private static final ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> BRAND_TYPE =
            new ParameterizedTypeReference<>() {};

    private Long createBrand(String name, String description) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BRANDS_PATH, HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, description)), BRAND_TYPE);
        return response.getBody().data().id();
    }

    @DisplayName("POST /api/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 정보로 등록하면, 200과 생성된 브랜드를 반환한다.")
        @Test
        void returnsCreatedBrand_whenValid() {
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                    BRANDS_PATH, HttpMethod.POST,
                    new HttpEntity<>(new BrandV1Dto.CreateBrandRequest("나이키", "스포츠")), BRAND_TYPE);

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드를 조회하면, 200과 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenExists() {
            Long brandId = createBrand("나이키", "스포츠");

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                    BRANDS_PATH + "/" + brandId, HttpMethod.GET, HttpEntity.EMPTY, BRAND_TYPE);

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404를 반환한다.")
        @Test
        void returns404_whenNotExists() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    BRANDS_PATH + "/9999", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("브랜드를 삭제하면, 이후 조회 시 404를 반환한다. (soft delete)")
        @Test
        void returns404OnGet_afterDelete() {
            Long brandId = createBrand("나이키", "스포츠");

            ResponseEntity<Object> deleteResponse = testRestTemplate.exchange(
                    BRANDS_PATH + "/" + brandId, HttpMethod.DELETE, HttpEntity.EMPTY, Object.class);
            assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();

            ResponseEntity<Object> getResponse = testRestTemplate.exchange(
                    BRANDS_PATH + "/" + brandId, HttpMethod.GET, HttpEntity.EMPTY, Object.class);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
