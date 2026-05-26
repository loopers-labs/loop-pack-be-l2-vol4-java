package com.loopers.interfaces.api;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * 페이지네이션 검증 E2E.
 * <p>명세 §5: page >= 0, 1 <= size <= 100. 위반 시 400. 거대 size 요청으로 인한 부하 차단 목적.</p>
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class PaginationValidationE2ETest {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String LDAP_ADMIN = "loopers.admin";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<String> get(String url) {
        return restTemplate.getForEntity(url, String.class);
    }

    private ResponseEntity<String> getAsAdmin(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LDAP_HEADER, LDAP_ADMIN);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @DisplayName("상품 목록 조회(고객) - 페이지네이션 검증 시")
    @Nested
    class CustomerProductSearch {

        @DisplayName("size가 100을 초과하면 400을 반환한다")
        @Test
        void returnsBadRequest_whenSizeOverMax() {
            assertThat(get("/api/v1/products?size=101").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 0이면 400을 반환한다")
        @Test
        void returnsBadRequest_whenSizeZero() {
            assertThat(get("/api/v1/products?size=0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("page가 음수이면 400을 반환한다")
        @Test
        void returnsBadRequest_whenPageNegative() {
            assertThat(get("/api/v1/products?page=-1").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size=100 경계값은 정상 통과한다")
        @Test
        void returnsOk_whenSizeAtMax() {
            assertThat(get("/api/v1/products?size=100").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("상품 목록 조회(어드민) - 페이지네이션 검증 시")
    @Nested
    class AdminProductSearch {

        @DisplayName("size가 100을 초과하면 400을 반환한다")
        @Test
        void returnsBadRequest_whenSizeOverMax() {
            assertThat(getAsAdmin("/api-admin/v1/products?size=101").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 목록 조회(어드민) - 페이지네이션 검증 시")
    @Nested
    class AdminBrandSearch {

        @DisplayName("size가 100을 초과하면 400을 반환한다")
        @Test
        void returnsBadRequest_whenSizeOverMax() {
            assertThat(getAsAdmin("/api-admin/v1/brands?size=101").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
