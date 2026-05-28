package com.loopers.brand.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
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
    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(TestRestTemplate testRestTemplate, BrandRepository brandRepository, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<ApiResponse<BrandV1Response.Detail>> getById(Long brandId) {
        ParameterizedTypeReference<ApiResponse<BrandV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT + "/" + brandId, HttpMethod.GET, null, type);
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("존재하는 brandId 로 조회하면 200 과 브랜드 정보(로고 포함)를 반환한다")
        void givenExistingBrandId_whenGet_thenReturnsBrand() {
            Brand saved = brandRepository.save(Brand.create("루퍼스", "설명", "https://cdn.loopers.com/l.png"));

            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response = getById(saved.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("루퍼스"),
                    () -> assertThat(response.getBody().data().logoUrl()).isEqualTo("https://cdn.loopers.com/l.png")
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 로 조회하면 404 NOT_FOUND 응답을 받는다")
        void givenNonExistingBrandId_whenGet_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<BrandV1Response.Detail>> response = getById(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/brands")
    @Nested
    class GetAll {

        @Test
        @DisplayName("등록된 브랜드 목록을 200 으로 반환한다 (인증 불필요)")
        void givenSavedBrands_whenGetAll_thenReturnsAllBrands() {
            brandRepository.save(Brand.create("A", "설명", null));
            brandRepository.save(Brand.create("B", "설명", null));

            ParameterizedTypeReference<ApiResponse<List<BrandV1Response.Detail>>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<BrandV1Response.Detail>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(BrandV1Response.Detail::name)
                            .containsExactlyInAnyOrder("A", "B")
            );
        }
    }
}
