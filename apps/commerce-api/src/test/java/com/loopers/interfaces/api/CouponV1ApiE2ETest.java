package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.fixture.UserFixture;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String USERS_URL = "/api/v1/users";
    private static final String ADMIN_COUPONS_URL = "/api-admin/v1/coupons";
    private static final String MY_COUPONS_URL = "/api/v1/users/me/coupons";
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.now().plusDays(30);

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> userResp = testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(userResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private UUID createTemplate(String name) {
        ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
            ADMIN_COUPONS_URL, HttpMethod.POST,
            new HttpEntity<>(new CouponV1Dto.CreateRequest(name, CouponType.RATE, 10L, 10000L, EXPIRED_AT), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private String issueUrl(UUID templateId) {
        return "/api/v1/coupons/" + templateId + "/issue";
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class Issue {

        @DisplayName("발급 시, 200 + AVAILABLE 상태의 발급 쿠폰을 반환한다.")
        @Test
        void returnsIssuedCoupon_whenValid() {
            UUID templateId = createTemplate("발급쿠폰");

            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = testRestTemplate.exchange(
                issueUrl(templateId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().templateId()).isEqualTo(templateId),
                () -> assertThat(response.getBody().data().status()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 템플릿으로 발급 시, 404 를 반환한다.")
        @Test
        void throwsNotFound_whenTemplateNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                issueUrl(UUID.randomUUID()), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("동일 템플릿을 다시 발급하면, 409 를 반환한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            UUID templateId = createTemplate("발급쿠폰");
            testRestTemplate.exchange(issueUrl(templateId), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<Void>() {});

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                issueUrl(templateId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("본인이 발급받은 쿠폰 목록을 상태와 함께 반환한다.")
        @Test
        void returnsMyCoupons() {
            UUID a = createTemplate("쿠폰A");
            UUID b = createTemplate("쿠폰B");
            testRestTemplate.exchange(issueUrl(a), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<Void>() {});
            testRestTemplate.exchange(issueUrl(b), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<Void>() {});

            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = testRestTemplate.exchange(
                MY_COUPONS_URL, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(2),
                () -> assertThat(response.getBody().data()).allMatch(c -> c.status() == UserCouponStatus.AVAILABLE)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssues {

        @DisplayName("관리자가 특정 템플릿의 발급 내역을 페이징해 조회한다.")
        @Test
        void returnsPagedIssues() {
            UUID templateId = createTemplate("인기쿠폰");
            testRestTemplate.exchange(issueUrl(templateId), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<Void>() {});

            ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.UserCouponResponse>>> response = testRestTemplate.exchange(
                ADMIN_COUPONS_URL + "/" + templateId + "/issues?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(1)
            );
        }
    }
}
