package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.apiadmin.brand.BrandAdminV1Dto;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BrandRepository brandRepository;
    @Autowired private BrandService brandService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_ID = "admin1";
    private static final String ADMIN_PW = "Admin1234!";

    @BeforeEach
    void setUp() {
        userRepository.save(new UserModel(
                new UserId(ADMIN_ID),
                new Password(passwordEncoder.encode(ADMIN_PW)),
                new Name("관리자"),
                new BirthDay("1990-01-01"),
                new Email("admin@test.com"),
                UserRole.ADMIN
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", ADMIN_ID);
        headers.set("X-Loopers-LoginPw", ADMIN_PW);
        return headers;
    }

    private BrandModel saveDefaultBrand(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class Register {

        @DisplayName("유효한 이름이면, 201 CREATED 와 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenNameIsValid() {
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<BrandAdminV1Dto.RegisterRequest> request = new HttpEntity<>(
                    new BrandAdminV1Dto.RegisterRequest("나이키"), adminHeaders());

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/brands", HttpMethod.POST, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("브랜드가 존재하면, 200 OK 와 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            BrandModel brand = saveDefaultBrand("나이키");
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/brands/" + brand.getId(), HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(brand.getId());
            assertThat(response.getBody().data().name()).isEqualTo("나이키");
        }

        @DisplayName("브랜드가 존재하지 않으면, 404 NOT_FOUND 를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/brands/999", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetList {

        @DisplayName("삭제되지 않은 브랜드만 페이징되어 반환된다.")
        @Test
        void returnsOnlyActiveBrandsWithPaging() {
            saveDefaultBrand("나이키");
            saveDefaultBrand("아디다스");
            BrandModel deleted = saveDefaultBrand("퓨마");
            brandService.delete(deleted.getId());

            ParameterizedTypeReference<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                    testRestTemplate.exchange("/api-admin/v1/brands?page=0&size=10", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
            assertThat(response.getBody().data().content()).hasSize(2);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class Update {

        @DisplayName("유효한 이름이면, 200 OK 와 변경된 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenNameIsValid() {
            BrandModel brand = saveDefaultBrand("나이키");
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<BrandAdminV1Dto.UpdateRequest> request = new HttpEntity<>(
                    new BrandAdminV1Dto.UpdateRequest("아디다스"), adminHeaders());

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/brands/" + brand.getId(), HttpMethod.PUT, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("아디다스");
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class Delete {

        @DisplayName("브랜드가 존재하면, 200 OK 를 반환한다.")
        @Test
        void returnsOk_whenBrandExists() {
            BrandModel brand = saveDefaultBrand("나이키");
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange("/api-admin/v1/brands/" + brand.getId(), HttpMethod.DELETE,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
