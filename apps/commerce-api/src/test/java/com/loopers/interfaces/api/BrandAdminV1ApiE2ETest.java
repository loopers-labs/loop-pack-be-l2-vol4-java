package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/brands";
    private static final String ADMIN_LDAP = "loopers.admin";

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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetAllBrands {

        @DisplayName("어드민 헤더가 있으면, 브랜드 목록을 반환한다.")
        @Test
        void returnsBrandList_whenAdminHeaderIsPresent() {
            brandJpaRepository.save(new Brand("브랜드A", "설명A"));
            brandJpaRepository.save(new Brand("브랜드B", "설명B"));

            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("어드민 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("어드민 헤더가 있으면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenAdminHeaderIsPresent() {
            Brand brand = brandJpaRepository.save(new Brand("무신사", "설명"));

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + brand.getId(),
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brand.getId())
            );
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/1",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 요청이면, 브랜드가 생성된다.")
        @Test
        void createsBrand_whenValidRequest() {
            String body = """
                {"name": "새 브랜드", "description": "새 브랜드 설명"}
                """;

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("새 브랜드")
            );
        }

        @DisplayName("이름이 없으면, 400 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsMissing() {
            String body = """
                {"description": "설명"}
                """;

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("어드민 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            String body = """
                {"name": "브랜드", "description": "설명"}
                """;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드를 수정하면, 수정된 정보가 반환된다.")
        @Test
        void updatesBrand_whenBrandExists() {
            Brand brand = brandJpaRepository.save(new Brand("원래", "원래 설명"));
            String body = """
                {"name": "수정 브랜드", "description": "수정 설명"}
                """;

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + brand.getId(),
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            String body = """
                {"name": "이름", "description": "설명"}
                """;

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 브랜드를 삭제하면, 200 응답을 반환한다.")
        @Test
        void deletesBrand_whenBrandExists() {
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + brand.getId(),
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
