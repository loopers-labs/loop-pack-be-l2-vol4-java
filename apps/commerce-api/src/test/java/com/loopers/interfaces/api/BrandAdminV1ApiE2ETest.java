package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
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
class BrandAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/admin/brands";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 브랜드 생성 후 응답 바디에서 ID 추출 */
    private UUID createBrand(String name, String description) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            BASE_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(name, description)),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().data().id();
    }

    @DisplayName("POST /api/v1/admin/brands")
    @Nested
    class Create {

        @DisplayName("유효한 요청으로 생성 시, 200 + 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenValidRequest() {
            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo(BrandFixture.NAME),
                () -> assertThat(response.getBody().data().description()).isEqualTo(BrandFixture.DESCRIPTION)
            );
        }

        @DisplayName("이름이 빈값이면, 400 을 반환한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateRequest("", BrandFixture.DESCRIPTION)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이미 존재하는 이름으로 생성 시, 409 를 반환한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, "다른 설명")),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/admin/brands/{id}")
    @Nested
    class Get {

        @DisplayName("존재하는 브랜드 조회 시, 200 + 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenExists() {
            // arrange
            UUID id = createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.GET,
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

        @DisplayName("존재하지 않는 ID 조회 시, 404 를 반환한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드 조회 시, 어드민은 200 을 반환한다.")
        @Test
        void returnsBrand_whenDeleted() {
            // arrange — 생성 후 삭제
            UUID id = createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);
            testRestTemplate.exchange(BASE_URL + "/" + id, HttpMethod.DELETE, null, new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert — 어드민은 삭제된 브랜드도 조회 가능
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/admin/brands")
    @Nested
    class GetList {

        @DisplayName("브랜드 목록 조회 시, 200 + 페이징 결과를 반환한다.")
        @Test
        void returnsPagedList_whenBrandsExist() {
            // arrange
            createBrand("나이키", BrandFixture.DESCRIPTION);
            createBrand("아디다스", BrandFixture.DESCRIPTION);
            createBrand("뉴발란스", BrandFixture.DESCRIPTION);

            // act
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=2", HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().getContent()).hasSize(2)
            );
        }
    }

    @DisplayName("PUT /api/v1/admin/brands/{id}")
    @Nested
    class Update {

        @DisplayName("유효한 요청으로 수정 시, 200 + 변경된 브랜드 정보를 반환한다.")
        @Test
        void returnUpdatedBrand_whenValidRequest() {
            // arrange
            UUID id = createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);
            BrandV1Dto.UpdateRequest updateRequest = new BrandV1Dto.UpdateRequest("아디다스", "독일 스포츠 브랜드");

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("독일 스포츠 브랜드")
            );
        }
    }

    @DisplayName("DELETE /api/v1/admin/brands/{id}")
    @Nested
    class Delete {

        @DisplayName("브랜드 삭제 후 고객용 GET 으로 조회 시, 404 를 반환한다.")
        @Test
        void returnsNotFound_whenDeletedAndAccessedByCustomer() {
            // arrange
            UUID id = createBrand(BrandFixture.NAME, BrandFixture.DESCRIPTION);

            // act — 삭제
            ResponseEntity<ApiResponse<Void>> deleteResponse = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert — 고객용 엔드포인트 → 404
            ResponseEntity<ApiResponse<Void>> getResponse = testRestTemplate.exchange(
                "/api/v1/brands/" + id, HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
