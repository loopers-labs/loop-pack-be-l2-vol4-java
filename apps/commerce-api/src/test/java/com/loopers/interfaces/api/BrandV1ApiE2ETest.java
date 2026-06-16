package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;

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

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT_BRANDS = "/api/v1/brands";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

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

    private BrandModel saveBrand(String name) {
        BrandModel brand = BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build();

        return brandJpaRepository.save(brand);
    }

    private BrandModel saveBrandWithoutDescription(String name) {
        BrandModel brand = BrandModel.builder()
            .rawName(name)
            .rawDescription(null)
            .build();

        return brandJpaRepository.save(brand);
    }

    @DisplayName("브랜드 조회 - GET /api/v1/brands/{brandId}")
    @Nested
    class ReadBrand {

        @DisplayName("활성 브랜드면, 무인증으로 200 OK와 함께 식별자·이름·설명만 반환된다.")
        @Test
        void returnsOk_withPublicFields_whenActive() {
            // arrange
            BrandModel savedBrand = saveBrand("감성 브랜드");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + savedBrand.getId(),
                HttpMethod.GET,
                null,
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("brandId", "name", "description"),
                () -> assertThat(((Number) response.getBody().data().get("brandId")).longValue()).isEqualTo(savedBrand.getId()),
                () -> assertThat(response.getBody().data().get("name")).isEqualTo("감성 브랜드"),
                () -> assertThat(response.getBody().data().get("description")).isEqualTo("감성을 담은 브랜드")
            );
        }

        @DisplayName("존재하지 않거나 삭제된 브랜드면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenAbsentOrDeleted() {
            // arrange
            BrandModel deletedBrand = saveBrand("삭제 브랜드");
            deletedBrand.delete();
            brandJpaRepository.saveAndFlush(deletedBrand);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> deletedResponse = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + deletedBrand.getId(),
                HttpMethod.GET,
                null,
                MAP_RESPONSE
            );
            ResponseEntity<ApiResponse<Map<String, Object>>> absentResponse = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/99999",
                HttpMethod.GET,
                null,
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(deletedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(deletedResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(deletedResponse.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(absentResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(absentResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(absentResponse.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("설명이 null인 브랜드를 조회하면, 응답의 description이 null로 반환된다.")
        @Test
        void returnsNullDescription_whenDescriptionIsNull() {
            // arrange
            BrandModel savedBrand = saveBrandWithoutDescription("설명없는 브랜드");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + savedBrand.getId(),
                HttpMethod.GET,
                null,
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("description")).isNull()
            );
        }
    }
}
