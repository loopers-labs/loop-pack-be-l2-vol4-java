package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String USER_BRAND_ENDPOINT = "/api/v1/brands";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String RAW_PASSWORD = "Password1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private UserModel testUser;

    @BeforeEach
    void setUp() {
        // 사용자 API(/api/v1/**) 는 AuthInterceptor 적용 대상이므로 테스트 유저를 미리 저장
        testUser = userJpaRepository.save(new UserModel(
            "testuser", passwordEncoder.encode(RAW_PASSWORD),
            "테스터", LocalDate.of(1990, 1, 15), "test@example.com"
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name, String description) {
        return brandJpaRepository.save(new BrandModel(name, description));
    }

    private HttpHeaders userAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", testUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    // ── 사용자 API ───────────────────────────────────────────────────────────

    @DisplayName("GET /api/v1/brands/{brandId} 요청 시,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 조회 시 200과 브랜드 정보가 반환된다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                USER_BRAND_ENDPOINT + "/" + saved.getId(),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 조회 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                USER_BRAND_ENDPOINT + "/999",
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── 어드민 API ───────────────────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/brands 요청 시,")
    @Nested
    class GetBrands {

        @DisplayName("브랜드 목록 조회 시 200과 활성 브랜드 페이지가 반환된다.")
        @Test
        void returnsBrandPage_whenBrandsExist() {
            // arrange
            saveBrand("Nike", "스포츠");
            saveBrand("Adidas", "독일");

            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response =
                testRestTemplate.exchange(ADMIN_BRAND_ENDPOINT, HttpMethod.GET, null, type);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }
    }

    @DisplayName("POST /api-admin/v1/brands 요청 시,")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 브랜드 등록 시 200과 등록된 브랜드 정보가 반환된다.")
        @Test
        void returnsBrand_whenValidRequestProvided() {
            // arrange
            BrandV1Dto.BrandCreateRequest request = new BrandV1Dto.BrandCreateRequest("Nike", "스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("Nike")
            );
        }

        @DisplayName("중복된 브랜드명으로 등록 시 409 CONFLICT가 반환된다.")
        @Test
        void returnsConflict_whenNameIsDuplicated() {
            // arrange
            saveBrand("Nike", "스포츠 브랜드");
            BrandV1Dto.BrandCreateRequest request = new BrandV1Dto.BrandCreateRequest("Nike", "다른 설명");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("브랜드명 없이 등록 시 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            BrandV1Dto.BrandCreateRequest request = new BrandV1Dto.BrandCreateRequest("", "설명");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId} 요청 시,")
    @Nested
    class UpdateBrand {

        @DisplayName("유효한 정보로 수정 시 200과 수정된 브랜드 정보가 반환된다.")
        @Test
        void returnsBrand_whenValidRequestProvided() {
            // arrange
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");
            BrandV1Dto.BrandUpdateRequest request = new BrandV1Dto.BrandUpdateRequest("Nike Pro", "프리미엄 스포츠");

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT + "/" + saved.getId(),
                HttpMethod.PUT, new HttpEntity<>(request), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("Nike Pro"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("프리미엄 스포츠")
            );
        }

        @DisplayName("존재하지 않는 브랜드 수정 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            BrandV1Dto.BrandUpdateRequest request = new BrandV1Dto.BrandUpdateRequest("Nike", "설명");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT + "/999",
                HttpMethod.PUT, new HttpEntity<>(request), type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId} 요청 시,")
    @Nested
    class DeleteBrand {

        @DisplayName("브랜드 삭제 후 사용자 API로 조회 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_afterBrandDeleted() {
            // arrange
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");

            // act - 삭제
            ParameterizedTypeReference<ApiResponse<Object>> deleteType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> deleteResponse = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT + "/" + saved.getId(),
                HttpMethod.DELETE, null, deleteType
            );

            // assert - 삭제 성공
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 이후 조회 시 NOT_FOUND
            ParameterizedTypeReference<ApiResponse<Void>> getType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> getResponse = testRestTemplate.exchange(
                USER_BRAND_ENDPOINT + "/" + saved.getId(),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), getType
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드 삭제 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT + "/999",
                HttpMethod.DELETE, null, type
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    /** Spring Page 응답 역직렬화를 위한 helper record */
    record PageResponse<T>(java.util.List<T> content, long totalElements, int totalPages, int size, int number) {}
}
