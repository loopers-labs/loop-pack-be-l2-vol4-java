package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthGuardV1ApiE2ETest {

    private static final String ADMIN_BRANDS = "/api-admin/v1/brands";
    private static final String LEGACY_ADMIN_BRANDS = "/api/v1/admin/brands";
    private static final String MEMBER_PRODUCTS = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminAuthGuardV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.ADMIN_LDAP, AuthHeaders.ADMIN_LDAP_VALUE);
        return headers;
    }

    @DisplayName("어드민 엔드포인트 가드")
    @Nested
    class Guard {

        @DisplayName("LDAP 헤더 없이 호출하면, 401 UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<ApiResponse<BrandAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN_BRANDS, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("잘못된 LDAP 헤더로 호출하면, 401 UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.ADMIN_LDAP, "someone.else");

            // when
            ResponseEntity<ApiResponse<BrandAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN_BRANDS, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-Ldap 이 loopers.admin 이면, 정상적으로 200 을 반환한다.")
        @Test
        void returnsOk_whenLdapHeaderIsAdmin() {
            // given
            brandFacade.create("나이키", "Just Do It");

            // when
            ResponseEntity<ApiResponse<BrandAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN_BRANDS, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1)
            );
        }
    }

    @DisplayName("경로/범위")
    @Nested
    class Scope {

        @DisplayName("구 경로 /api/v1/admin/** 는 더 이상 매핑되지 않아, 404 를 반환한다.")
        @Test
        void returnsNotFound_whenCallingLegacyAdminPath() {
            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                LEGACY_ADMIN_BRANDS, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("회원 엔드포인트(/api/v1/**)는 LDAP 가드의 영향을 받지 않아, 헤더 없이도 200 을 반환한다.")
        @Test
        void memberEndpointIsNotAffectedByAdminGuard() {
            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                MEMBER_PRODUCTS, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
