package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.BrandDto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandApiE2ETest {

    private static final String ADMIN_API_URL = "/api-admin/v1/brands";
    private static final String PUBLIC_API_URL = "/api/v1/brands";
    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_HEADER_VALUE = "loopers.admin";

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

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("ADMIN 헤더 없이 요청하면, 401을 반환한다.")
        @Test
        void returns401_whenAdminHeaderIsMissing() {
            // arrange
            BrandDto.CreateRequest request = new BrandDto.CreateRequest("나이키");

            // act
            ResponseEntity<ApiResponse<BrandDto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_API_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 이름으로 등록하면, 201과 브랜드 정보를 반환한다.")
        @Test
        void returns201_whenBrandCreatedSuccessfully() {
            // arrange
            BrandDto.CreateRequest request = new BrandDto.CreateRequest("나이키");
            HttpHeaders headers = new HttpHeaders();
            headers.set(ADMIN_HEADER, ADMIN_HEADER_VALUE);

            // act
            ResponseEntity<ApiResponse<BrandDto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_API_URL, HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
        }

        @DisplayName("이미 존재하는 이름으로 등록하면, 409를 반환한다.")
        @Test
        void returns409_whenBrandNameAlreadyExists() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키"));
            BrandDto.CreateRequest request = new BrandDto.CreateRequest("나이키");
            HttpHeaders headers = new HttpHeaders();
            headers.set(ADMIN_HEADER, ADMIN_HEADER_VALUE);

            // act
            ResponseEntity<ApiResponse<BrandDto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_API_URL, HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 brandId로 조회하면, 200과 브랜드 정보를 반환한다.")
        @Test
        void returns200_whenBrandExists() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키"));

            // act
            ResponseEntity<ApiResponse<BrandDto.BrandResponse>> response = testRestTemplate.exchange(
                PUBLIC_API_URL + "/" + saved.getId(), HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 brandId로 조회하면, 404를 반환한다.")
        @Test
        void returns404_whenBrandNotFound() {
            // act
            ResponseEntity<ApiResponse<BrandDto.BrandResponse>> response = testRestTemplate.exchange(
                PUBLIC_API_URL + "/999", HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetBrands {

        @DisplayName("ADMIN이 조회하면, 200과 브랜드 목록을 반환한다.")
        @Test
        void returns200WithBrandList_whenAdminRequests() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키"));
            brandJpaRepository.save(new BrandModel("아디다스"));
            HttpHeaders headers = new HttpHeaders();
            headers.set(ADMIN_HEADER, ADMIN_HEADER_VALUE);

            // act
            ResponseEntity<ApiResponse<BrandDto.BrandPageResponse>> response = testRestTemplate.exchange(
                ADMIN_API_URL, HttpMethod.GET,
                new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().brands()).hasSize(2);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("유효한 이름으로 수정하면, 200과 수정된 브랜드를 반환한다.")
        @Test
        void returns200_whenBrandUpdatedSuccessfully() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키"));
            BrandDto.UpdateRequest request = new BrandDto.UpdateRequest("아디다스");
            HttpHeaders headers = new HttpHeaders();
            headers.set(ADMIN_HEADER, ADMIN_HEADER_VALUE);

            // act
            ResponseEntity<ApiResponse<BrandDto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_API_URL + "/" + saved.getId(), HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("아디다스");
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 브랜드를 삭제하면, 204를 반환한다.")
        @Test
        void returns204_whenBrandDeletedSuccessfully() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키"));
            HttpHeaders headers = new HttpHeaders();
            headers.set(ADMIN_HEADER, ADMIN_HEADER_VALUE);

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ADMIN_API_URL + "/" + saved.getId(), HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
