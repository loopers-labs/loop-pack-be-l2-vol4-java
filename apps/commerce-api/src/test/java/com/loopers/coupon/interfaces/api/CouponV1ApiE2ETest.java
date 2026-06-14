package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.coupon.domain.UserCouponStatus;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.user.interfaces.api.UserV1Dto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_COUPON_ISSUE = "/api/v1/coupons/{couponId}/issue";
    private static final String ENDPOINT_MY_COUPONS = "/api/v1/users/me/coupons";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";
    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final String AVAILABLE_COUPON_NAME = "1주년 사용 가능 쿠폰";
    private static final String USED_COUPON_NAME = "1주년 사용 완료 쿠폰";
    private static final String EXPIRED_COUPON_NAME = "1주년 만료 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime PAST_EXPIRED_AT = ZonedDateTime.parse("2026-01-01T00:00:00+09:00");
    private static final ZonedDateTime USED_AT = ZonedDateTime.parse("2026-06-01T12:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final TestRestTemplate testRestTemplate;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponRepository userCouponRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
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
                () -> assertThat(data.displayStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(data.issuedAt()).isNotNull()
            );
        }

        @DisplayName("이미 발급된 쿠폰 템플릿 ID로 다시 요청하면, 200 OK와 기존 발급 쿠폰을 반환한다.")
        @Test
        void returnsExistingCoupon_whenCouponIsAlreadyIssued() {
            // arrange
            signUpUser();
            CouponTemplate couponTemplate = createCouponTemplate();
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> firstResponse = issueCoupon(couponTemplate.getId(), authHeaders());

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = issueCoupon(couponTemplate.getId(), authHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(firstResponse.getBody().data().id())
            );
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

        @DisplayName("만료된 쿠폰 템플릿 ID로 요청하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenCouponTemplateIsExpired() {
            // arrange
            signUpUser();
            CouponTemplate couponTemplate = createCouponTemplate(COUPON_NAME, PAST_EXPIRED_AT);

            // act
            ResponseEntity<ApiResponse<Object>> response = issueCouponForError(couponTemplate.getId(), authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("인증 사용자가 쿠폰 목록을 조회하면, 보유 쿠폰의 상태와 쿠폰 정보를 함께 반환한다.")
        @Test
        void returnsMyCouponsWithStatus_whenAuthenticatedUserRequestsCoupons() {
            // arrange
            Long userId = signUpUser();
            CouponTemplate availableCoupon = createCouponTemplate(AVAILABLE_COUPON_NAME, EXPIRED_AT);
            CouponTemplate usedCoupon = createCouponTemplate(USED_COUPON_NAME, EXPIRED_AT);
            CouponTemplate expiredCoupon = createCouponTemplate(EXPIRED_COUPON_NAME, PAST_EXPIRED_AT);
            issueCoupon(availableCoupon.getId(), authHeaders());
            issueCoupon(usedCoupon.getId(), authHeaders());
            userCouponRepository.save(UserCoupon.issue(userId, expiredCoupon.getId(), expiredCoupon));
            useCoupon(userId, usedCoupon.getId());

            // act
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = getMyCoupons(authHeaders());

            // assert
            List<CouponV1Dto.UserCouponResponse> coupons = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(coupons)
                    .extracting(CouponV1Dto.UserCouponResponse::name)
                    .containsExactly(EXPIRED_COUPON_NAME, USED_COUPON_NAME, AVAILABLE_COUPON_NAME),
                () -> assertThat(coupons)
                    .extracting(CouponV1Dto.UserCouponResponse::displayStatus)
                    .containsExactly(UserCouponStatus.EXPIRED, UserCouponStatus.USED, UserCouponStatus.AVAILABLE),
                () -> assertThat(coupons)
                    .extracting(CouponV1Dto.UserCouponResponse::discountValue)
                    .containsExactly(2_000L, 2_000L, 2_000L),
                () -> assertThat(coupons)
                    .extracting(CouponV1Dto.UserCouponResponse::minimumOrderAmount)
                    .containsExactly(10_000L, 10_000L, 10_000L)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            // act
            ResponseEntity<ApiResponse<Object>> response = getMyCouponsForError(new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private CouponTemplate createCouponTemplate() {
        return createCouponTemplate(COUPON_NAME, EXPIRED_AT);
    }

    private CouponTemplate createCouponTemplate(String name, ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(CouponTemplate.create(
            name,
            CouponType.FIXED,
            2_000L,
            10_000L,
            expiredAt,
            FIXED_POLICY
        ));
    }

    private Long signUpUser() {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            LOGIN_ID,
            PASSWORD,
            "김상호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
            ENDPOINT_USERS,
            HttpMethod.POST,
            new HttpEntity<>(request),
            responseType
        );
        return response.getBody().data().id();
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

    private ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> getMyCoupons(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_MY_COUPONS,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> getMyCouponsForError(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_MY_COUPONS,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private void useCoupon(Long userId, Long couponTemplateId) {
        UserCoupon userCoupon = userCouponRepository.findIssuedCoupon(userId, couponTemplateId)
            .orElseThrow();
        userCoupon.use(userId, USED_AT);
        userCouponRepository.save(userCoupon);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
