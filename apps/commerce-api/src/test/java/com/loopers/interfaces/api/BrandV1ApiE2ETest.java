package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandAdminInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/brands/{brandId}";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 active 브랜드 id 로 요청하면, 200 과 브랜드 정보가 반환된다.")
        @Test
        void returnsBrand_whenBrandExistsAndIsActive() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.CustomerResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.CustomerResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().id()).isEqualTo(created.id()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("Just Do It")
            );
        }

        @DisplayName("응답 JSON 의 data 객체에는 운영 메타 필드(createdAt/updatedAt/deletedAt)가 포함되지 않는다.")
        @Test
        void doesNotExposeOperationalMeta_inResponseJson() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            ResponseEntity<JsonNode> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class, created.id()
            );

            // then
            JsonNode data = response.getBody().get("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.has("id")).isTrue(),
                () -> assertThat(data.has("name")).isTrue(),
                () -> assertThat(data.has("description")).isTrue(),
                () -> assertThat(data.has("createdAt")).isFalse(),
                () -> assertThat(data.has("updatedAt")).isFalse(),
                () -> assertThat(data.has("deletedAt")).isFalse()
            );
        }

        @DisplayName("존재하지 않는 id 로 요청하면, 404 와 BRAND_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.CustomerResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.CustomerResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, missingId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("BRAND_NOT_FOUND")
            );
        }

        @DisplayName("soft-delete 된 브랜드 id 로 요청하면, 404 와 BRAND_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenBrandIsSoftDeleted() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);

            // when
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.CustomerResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.CustomerResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("BRAND_NOT_FOUND")
            );
        }
    }
}
