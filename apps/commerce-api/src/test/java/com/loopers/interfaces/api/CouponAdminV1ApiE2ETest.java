package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/coupons";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private IssuedCouponJpaRepository issuedCouponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

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

    private CouponEntity saveCoupon(String name) {
        return couponJpaRepository.save(new CouponEntity(
            name, CouponType.FIXED, BigDecimal.valueOf(1000),
            BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)
        ));
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetCoupons {

        @DisplayName("어드민 헤더가 있으면, 쿠폰 목록을 반환한다.")
        @Test
        void returnsCouponList_whenAdminHeaderIsPresent() {
            saveCoupon("쿠폰1");
            saveCoupon("쿠폰2");
            saveCoupon("쿠폰3");

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=2",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> page = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) page.get("content")).hasSize(2),
                () -> assertThat(page.get("totalElements")).isEqualTo(3),
                () -> assertThat(page.get("totalPages")).isEqualTo(2)
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {

        @DisplayName("존재하는 쿠폰을 조회하면, 상세 정보를 반환한다.")
        @Test
        void returnsCouponDetail_whenCouponExists() {
            CouponEntity coupon = saveCoupon("여름 할인 쿠폰");

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                BASE_URL + "/" + coupon.getId(),
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.get("name")).isEqualTo("여름 할인 쿠폰"),
                () -> assertThat(data.get("id")).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 조회하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 요청이면, 쿠폰이 생성된다.")
        @Test
        void createsCoupon_whenValidRequest() {
            String body = """
                {"name": "웰컴 쿠폰", "type": "FIXED", "value": 2000, "minOrderAmount": 10000, "expiredAt": "2030-12-31T23:59:59+09:00"}
                """;

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.get("name")).isEqualTo("웰컴 쿠폰"),
                () -> assertThat(data.get("id")).isNotNull()
            );
        }

        @DisplayName("이름이 없으면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            String body = """
                {"name": "", "type": "FIXED", "value": 1000, "minOrderAmount": 5000, "expiredAt": "2030-12-31T23:59:59+09:00"}
                """;

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("어드민 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            String body = """
                {"name": "쿠폰", "type": "FIXED", "value": 1000, "minOrderAmount": 5000, "expiredAt": "2030-12-31T23:59:59+09:00"}
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

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        @DisplayName("존재하는 쿠폰을 수정하면, 수정된 쿠폰 정보를 반환한다.")
        @Test
        void updatesCoupon_whenCouponExists() {
            CouponEntity coupon = saveCoupon("기존 쿠폰");
            String body = """
                {"name": "수정된 쿠폰", "type": "RATE", "value": 10, "minOrderAmount": 20000, "expiredAt": "2030-06-30T23:59:59+09:00"}
                """;

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                BASE_URL + "/" + coupon.getId(),
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.get("name")).isEqualTo("수정된 쿠폰"),
                () -> assertThat(data.get("type")).isEqualTo("RATE")
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 수정하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            String body = """
                {"name": "이름", "type": "FIXED", "value": 1000, "minOrderAmount": 5000, "expiredAt": "2030-12-31T23:59:59+09:00"}
                """;

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "/9999",
                HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰을 삭제하면, 200을 반환한다.")
        @Test
        void deletesCoupon_whenCouponExists() {
            CouponEntity coupon = saveCoupon("삭제할 쿠폰");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + coupon.getId(),
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("이미 삭제된 쿠폰을 삭제하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenCouponAlreadyDeleted() {
            CouponEntity coupon = saveCoupon("삭제된 쿠폰");
            coupon.delete();
            couponJpaRepository.save(coupon);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + coupon.getId(),
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("발급 쿠폰 목록을 반환한다.")
        @Test
        void returnsIssuedCouponList_whenCouponExists() {
            CouponEntity coupon = saveCoupon("발급 테스트 쿠폰");
            issuedCouponJpaRepository.save(new IssuedCouponEntity(coupon.getId(), 1L, ZonedDateTime.now().plusDays(7)));
            issuedCouponJpaRepository.save(new IssuedCouponEntity(coupon.getId(), 2L, ZonedDateTime.now().plusDays(7)));

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                BASE_URL + "/" + coupon.getId() + "/issues?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> page = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) page.get("content")).hasSize(2)
            );
        }

        @DisplayName("존재하지 않는 쿠폰의 발급 목록을 조회하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                BASE_URL + "/9999/issues?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
