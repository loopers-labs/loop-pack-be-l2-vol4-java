package com.loopers.brand.interfaces;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.support.response.ApiResponse;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/brands";

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

    @DisplayName("GET /api/v1/brands/{id}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 brandId이면, 200 OK와 BrandResponse를 반환한다.")
        @Test
        void returnsBrandResponse_whenBrandExists() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 brandId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("숫자가 아닌 id이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenIdIsNotNumber() {
            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/나나", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
