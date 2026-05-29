package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/brands";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 정보로 브랜드를 등록하면, 200 OK와 등록된 브랜드 정보를 반환한다.")
        @Test
        void returnsOk_whenValidRequestIsGiven() {
            // arrange
            BrandAdminV1Dto.CreateBrandRequest body = new BrandAdminV1Dto.CreateBrandRequest("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetBrands {

        @DisplayName("브랜드 목록을 페이징으로 조회하면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsOk_whenValidPageParamsAreGiven() {
            // arrange
            brandApplicationService.create("나이키", "스포츠 브랜드");
            brandApplicationService.create("아디다스", "글로벌 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("page가 음수이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPageIsNegative() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=-1", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 0이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeIsZero() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?size=0", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 100을 초과하면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeExceedsMax() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?size=101", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 id로 요청하면, 200 OK와 브랜드 정보를 반환한다.")
        @Test
        void returnsOk_whenBrandExists() {
            // arrange
            BrandInfo brand = brandApplicationService.create("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + brand.id(), HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().id()).isEqualTo(brand.id()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("유효한 정보로 브랜드를 수정하면, 200 OK와 수정된 브랜드 정보를 반환한다.")
        @Test
        void returnsOk_whenValidRequestIsGiven() {
            // arrange
            BrandInfo brand = brandApplicationService.create("나이키", "스포츠 브랜드");
            BrandAdminV1Dto.UpdateBrandRequest body = new BrandAdminV1Dto.UpdateBrandRequest("아디다스", "글로벌 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + brand.id(), HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("글로벌 스포츠 브랜드")
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 브랜드를 삭제하면, 200 OK 응답을 반환한다.")
        @Test
        void returnsOk_whenBrandExists() {
            // arrange
            BrandInfo brand = brandApplicationService.create("나이키", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + brand.id(), HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
