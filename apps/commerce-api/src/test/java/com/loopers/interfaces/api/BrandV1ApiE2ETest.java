package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BrandV1ApiE2ETest {

    private static final String ENDPOINT_GET_BRAND = "/api/v1/brands/{brandId}";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandRepository brandRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("등록된 브랜드의 ID 로 조회하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandModel saved = brandRepository.save(new BrandModel("Nike", "스포츠 브랜드"));

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_BRAND, HttpMethod.GET, null, responseType, saved.getId());

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("Nike"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID 로 조회하면, 404 NOT_FOUND 를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            Long nonExistentId = 999_999L;

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_BRAND, HttpMethod.GET, null, responseType, nonExistentId);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
