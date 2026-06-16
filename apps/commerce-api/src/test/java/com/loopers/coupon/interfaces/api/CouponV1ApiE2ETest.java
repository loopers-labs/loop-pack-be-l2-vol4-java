package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponAdminService;
import com.loopers.coupon.application.CouponCommand;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
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

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final ZonedDateTime EXPIRES = ZonedDateTime.parse("2030-12-31T23:59:59+09:00");

    private final TestRestTemplate testRestTemplate;
    private final UserAccountService userAccountService;
    private final CouponAdminService couponAdminService;
    private final DatabaseCleanUp databaseCleanUp;

    private Long couponId;

    @Autowired
    public CouponV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserAccountService userAccountService,
            CouponAdminService couponAdminService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userAccountService = userAccountService;
        this.couponAdminService = couponAdminService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        userAccountService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        ));
        couponId = couponAdminService.create(
                new CouponCommand.Create("신규가입 3천원", CouponType.FIXED, 3_000L, 10_000L, EXPIRES)
        ).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<CouponV1Response.IssueDetail>> issue(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<CouponV1Response.IssueDetail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST, new HttpEntity<>(headers), type);
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class Issue {

        @Test
        @DisplayName("인증 사용자가 발급하면 200 과 AVAILABLE 발급 쿠폰을 반환한다")
        void givenAuthUser_whenIssue_thenReturnsIssuedCoupon() {
            ResponseEntity<ApiResponse<CouponV1Response.IssueDetail>> response = issue(couponId, authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(couponId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @Test
        @DisplayName("같은 템플릿을 두 번 발급하면 두 번째는 409 를 반환한다")
        void givenAlreadyIssued_whenIssueAgain_thenReturnsConflict() {
            issue(couponId, authHeaders());

            ResponseEntity<ApiResponse<CouponV1Response.IssueDetail>> second = issue(couponId, authHeaders());

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 을 반환한다")
        void givenNoAuth_whenIssue_thenReturnsUnauthorized() {
            ResponseEntity<ApiResponse<CouponV1Response.IssueDetail>> response = issue(couponId, new HttpHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("존재하지 않는 템플릿이면 404 를 반환한다")
        void givenMissingTemplate_whenIssue_thenReturnsNotFound() {
            ResponseEntity<ApiResponse<CouponV1Response.IssueDetail>> response = issue(99999L, authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @Test
        @DisplayName("발급받은 쿠폰을 AVAILABLE 상태로 목록 조회한다")
        void givenIssued_whenGetMyCoupons_thenReturnsAvailable() {
            issue(couponId, authHeaders());

            ParameterizedTypeReference<ApiResponse<CouponV1Response.MyCoupons>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Response.MyCoupons>> response = testRestTemplate.exchange(
                    "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(authHeaders()), type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().coupons()).hasSize(1),
                    () -> assertThat(response.getBody().data().coupons().get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 을 반환한다")
        void givenNoAuth_whenGetMyCoupons_thenReturnsUnauthorized() {
            ParameterizedTypeReference<ApiResponse<CouponV1Response.MyCoupons>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Response.MyCoupons>> response = testRestTemplate.exchange(
                    "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
