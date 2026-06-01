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
class AdminBrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/admin/brands";

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

    @DisplayName("POST /api/v1/admin/brands")
    @Nested
    class CreateBrand {

        @DisplayName("정상 요청이면, 200 OK와 생성된 BrandResponse를 반환한다.")
        @Test
        void returnsBrandResponse_whenRequestIsValid() {
            // arrange
            AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("name이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest("", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PATCH /api/v1/admin/brands/{id}")
    @Nested
    class UpdateBrand {

        @DisplayName("정상 요청이면, 200 OK와 수정된 BrandResponse를 반환한다.")
        @Test
        void returnsUpdatedBrandResponse_whenRequestIsValid() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            AdminBrandV1Dto.UpdateRequest request = new AdminBrandV1Dto.UpdateRequest("아디다스", "독일 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 brandId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // arrange
            AdminBrandV1Dto.UpdateRequest request = new AdminBrandV1Dto.UpdateRequest("아디다스", "독일 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("name이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            AdminBrandV1Dto.UpdateRequest request = new AdminBrandV1Dto.UpdateRequest("", "독일 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/admin/brands/{id}")
    @Nested
    class DeleteBrand {

        @DisplayName("정상 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenRequestIsValid() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.DELETE, new HttpEntity<>(null), Void.class);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("존재하지 않는 brandId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
