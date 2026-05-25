package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
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

    private static final String BASE_URL = "/api/v1/brands";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID를 주면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandInfo_whenValidIdIsProvided() {
            Brand brand = brandJpaRepository.save(new Brand("무신사", "무신사 브랜드"));

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + brand.getId(),
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("무신사"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("무신사 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 주면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드 ID를 주면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandIsSoftDeleted() {
            Brand brand = brandJpaRepository.save(new Brand("삭제 브랜드", "설명"));
            brand.delete();
            brandJpaRepository.save(brand);

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + brand.getId(),
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
