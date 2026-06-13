package com.loopers.interfaces.api;

import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.domain.coupon.CouponType;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String COUPONS_URL = "/api/v1/coupons";
    private static final String MY_COUPONS_URL = "/api/v1/users/me/coupons";
    private static final String USERS_URL = "/api/v1/users";

    private static final String LOGIN_ID = "couponuser";
    private static final String LOGIN_PW = "pAssWord1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private IssuedCouponJpaRepository issuedCouponJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(new com.loopers.interfaces.api.user.UserV1Dto.UserJoinRequest(
                LOGIN_ID, LOGIN_PW, "쿠폰유저", java.time.LocalDate.of(2000, 1, 1), "coupon@test.com"
            )),
            new ParameterizedTypeReference<ApiResponse<com.loopers.interfaces.api.user.UserV1Dto.UserResponse>>() {}
        );
        userId = userJpaRepository.findByLoginId(LOGIN_ID).get().getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    private CouponEntity saveCoupon(String name) {
        return couponJpaRepository.save(new CouponEntity(
            name, CouponType.FIXED, BigDecimal.valueOf(1000),
            BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)
        ));
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("로그인 유저가 존재하는 쿠폰을 발급하면, 발급된 쿠폰 정보를 반환한다.")
        @Test
        void returnsIssuedCoupon_whenUserIssuesCoupon() {
            CouponEntity coupon = saveCoupon("신규 회원 쿠폰");

            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                COUPONS_URL + "/" + coupon.getId() + "/issue",
                HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.get("issuedCouponId")).isNotNull(),
                () -> assertThat(data.get("couponId")).isEqualTo(coupon.getId().intValue())
            );
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            CouponEntity coupon = saveCoupon("쿠폰");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                COUPONS_URL + "/" + coupon.getId() + "/issue",
                HttpMethod.POST, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                COUPONS_URL + "/9999/issue",
                HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("로그인 유저가 내 쿠폰을 조회하면, 발급된 쿠폰 목록을 반환한다.")
        @Test
        void returnsMyCouponList_whenUserHasCoupons() {
            CouponEntity coupon1 = saveCoupon("쿠폰A");
            CouponEntity coupon2 = saveCoupon("쿠폰B");
            issuedCouponJpaRepository.save(new IssuedCouponEntity(coupon1.getId(), userId, ZonedDateTime.now().plusDays(7)));
            issuedCouponJpaRepository.save(new IssuedCouponEntity(coupon2.getId(), userId, ZonedDateTime.now().plusDays(7)));

            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                MY_COUPONS_URL,
                HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(2)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                MY_COUPONS_URL,
                HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
