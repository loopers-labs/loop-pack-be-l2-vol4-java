package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("브랜드가 존재하면, 200 OK와 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            BrandModel brand = brandRepository.save(new BrandModel("나이키"));
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange("/api/v1/brands/" + brand.getId(), HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(brand.getId());
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
        }

        @DisplayName("브랜드가 존재하지 않으면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange("/api/v1/brands/999", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
