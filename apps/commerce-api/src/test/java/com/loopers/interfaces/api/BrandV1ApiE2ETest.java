package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ADMIN_BASE_URL    = "/api/v1/admin/brands";
    private static final String CUSTOMER_BASE_URL = "/api/v1/brands";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 어드민 API로 브랜드 생성 후 ID 반환 */
    private UUID createBrand(String name, String description) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ADMIN_BASE_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(name, description)),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().data().id();
    }

    @DisplayName("GET /api/v1/brands/{id}")
    @Nested
    class GetActive {

        @DisplayName("활성 브랜드 조회 시, 200 + 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenActive() {
            // arrange
            UUID id = createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                CUSTOMER_BASE_URL + "/" + id, HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(id),
                () -> assertThat(response.getBody().data().name()).isEqualTo(BrandFixture.NAME)
            );
        }

        @DisplayName("삭제된 브랜드 조회 시, 404 를 반환한다.")
        @Test
        void throwsNotFound_whenDeleted() {
            // arrange — 생성 후 소프트딜리트
            UUID id = createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);
            testRestTemplate.exchange(ADMIN_BASE_URL + "/" + id, HttpMethod.DELETE, null, new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                CUSTOMER_BASE_URL + "/" + id, HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 ID 조회 시, 404 를 반환한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                CUSTOMER_BASE_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
