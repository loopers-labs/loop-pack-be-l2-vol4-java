package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT_BRAND_DETAIL = "/api/v1/brands/{brandId}";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    BrandV1ApiE2ETest(
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

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID로 요청하면, 200 OK 와 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandIdExists() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = getBrand(saved.getId());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("애플"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("기술과 디자인으로 일상을 새롭게 만드는 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 요청하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandIdDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = getBrand(999_999L);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> getBrand(Long brandId) {
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_BRAND_DETAIL,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            responseType,
            brandId
        );
    }
}
