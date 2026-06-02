package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_COUPON_ISSUE = "/api/v1/coupons/{couponId}/issue";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";
    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final TestRestTemplate testRestTemplate;
    private final CouponTemplateRepository couponTemplateRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponTemplateRepository couponTemplateRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponTemplateRepository = couponTemplateRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("인증 사용자가 발급 가능한 쿠폰 템플릿 ID로 요청하면, 201 CREATED와 발급 쿠폰 정보를 반환한다.")
        @Test
        void returnsIssuedCoupon_whenAuthenticatedUserAndCouponTemplateExist() {
            // arrange
            signUpUser();
            CouponTemplate couponTemplate = createCouponTemplate();

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = issueCoupon(couponTemplate.getId(), authHeaders());

            // assert
            CouponV1Dto.UserCouponResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.couponTemplateId()).isEqualTo(couponTemplate.getId()),
                () -> assertThat(data.name()).isEqualTo(COUPON_NAME),
                () -> assertThat(data.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(data.discountValue()).isEqualTo(2_000L),
                () -> assertThat(data.minimumOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(data.expiredAt()).isEqualTo(EXPIRED_AT),
                () -> assertThat(data.status()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(data.issuedAt()).isNotNull()
            );
        }

        @DisplayName("이미 발급된 쿠폰 템플릿 ID로 다시 요청하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenCouponIsAlreadyIssued() {
            // arrange
            signUpUser();
            CouponTemplate couponTemplate = createCouponTemplate();
            issueCoupon(couponTemplate.getId(), authHeaders());

            // act
            ResponseEntity<ApiResponse<Object>> response = issueCouponForError(couponTemplate.getId(), authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            // arrange
            CouponTemplate couponTemplate = createCouponTemplate();

            // act
            ResponseEntity<ApiResponse<Object>> response = issueCouponForError(couponTemplate.getId(), new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿 ID로 요청하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponTemplateDoesNotExist() {
            // arrange
            signUpUser();
            Long couponId = 999_999L;

            // act
            ResponseEntity<ApiResponse<Object>> response = issueCouponForError(couponId, authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private CouponTemplate createCouponTemplate() {
        return couponTemplateRepository.save(CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        ));
    }

    private void signUpUser() {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            LOGIN_ID,
            PASSWORD,
            "김상호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_USERS, request, ApiResponse.class);
    }

    private ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> issueCoupon(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPON_ISSUE,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            responseType,
            couponId
        );
    }

    private ResponseEntity<ApiResponse<Object>> issueCouponForError(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPON_ISSUE,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            responseType,
            couponId
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
