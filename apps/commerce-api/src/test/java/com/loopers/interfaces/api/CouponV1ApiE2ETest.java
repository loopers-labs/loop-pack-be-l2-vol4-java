package com.loopers.interfaces.api;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.infrastructure.coupon.CouponPolicyJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String MY_COUPONS_ENDPOINT = "/api/v1/users/me/coupons";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Abcd1234!";

    private final TestRestTemplate testRestTemplate;
    private final UserFacade userFacade;
    private final CouponPolicyJpaRepository couponPolicyJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @Autowired
    public CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserFacade userFacade,
        CouponPolicyJpaRepository couponPolicyJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userFacade = userFacade;
        this.couponPolicyJpaRepository = couponPolicyJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        userId = userFacade.signUp(new UserCommand.SignUp(
            LOGIN_ID,
            LOGIN_PW,
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        return headers;
    }

    private CouponPolicy savePolicy(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponPolicyJpaRepository.save(new CouponPolicy(name, type, value, minOrderAmount, expiredAt));
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 인증 헤더와 존재하는 쿠폰 정책으로 요청하면, 200 과 AVAILABLE 상태의 Response 를 반환하고 user_coupon 이 생성된다.")
        @Test
        void returnsAvailableCoupon_whenAuthAndPolicyExist() {
            // given
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);
            CouponPolicy policy = savePolicy("5천원 할인", CouponType.FIXED, 5_000L, 10_000L, expiredAt);
            String endpoint = "/api/v1/coupons/" + policy.getId() + "/issue";

            // when
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = testRestTemplate.exchange(
                endpoint, HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType
            );

            // then
            CouponV1Dto.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.couponPolicyId()).isEqualTo(policy.getId()),
                () -> assertThat(data.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(data.discountValue()).isEqualTo(5_000L),
                () -> assertThat(data.minOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(data.status()).isEqualTo(CouponDisplayStatus.AVAILABLE),
                () -> assertThat(data.usedAt()).isNull(),
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1L)
            );
        }

        @DisplayName("인증 헤더가 누락되면, UNAUTHORIZED 를 반환하고 user_coupon 이 생성되지 않는다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // given
            CouponPolicy policy = savePolicy("5천원 할인", CouponType.FIXED, 5_000L, null, ZonedDateTime.now().plusDays(30));
            String endpoint = "/api/v1/coupons/" + policy.getId() + "/issue";

            // when
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = testRestTemplate.exchange(
                endpoint, HttpMethod.POST, new HttpEntity<>(null, new HttpHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(userCouponJpaRepository.count()).isZero()
            );
        }

        @DisplayName("존재하지 않는 쿠폰 정책으로 요청하면, NOT_FOUND 와 COUPON_POLICY_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenPolicyDoesNotExist() {
            // given
            String endpoint = "/api/v1/coupons/999/issue";

            // when
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = testRestTemplate.exchange(
                endpoint, HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("COUPON_POLICY_NOT_FOUND"),
                () -> assertThat(userCouponJpaRepository.count()).isZero()
            );
        }

        @DisplayName("이미 만료된 쿠폰 정책으로 요청하면, COUPON_EXPIRED 코드를 반환하고 user_coupon 이 생성되지 않는다.")
        @Test
        void returnsCouponExpired_whenPolicyIsExpired() {
            // given
            CouponPolicy policy = savePolicy("만료 쿠폰", CouponType.FIXED, 5_000L, null, ZonedDateTime.now().minusDays(1));
            String endpoint = "/api/v1/coupons/" + policy.getId() + "/issue";

            // when
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.Response>> response = testRestTemplate.exchange(
                endpoint, HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("COUPON_EXPIRED"),
                () -> assertThat(userCouponJpaRepository.count()).isZero()
            );
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("유효한 인증으로 요청하면, 200 과 함께 AVAILABLE/USED/EXPIRED 표시 상태가 파생된 내 쿠폰 목록을 반환한다.")
        @Test
        void returnsCouponsWithDisplayStatus_whenAuthenticated() {
            // given
            ZonedDateTime now = ZonedDateTime.now();
            CouponPolicy validPolicy = savePolicy("유효 쿠폰", CouponType.FIXED, 5_000L, null, now.plusDays(30));
            CouponPolicy expiredPolicy = savePolicy("만료 쿠폰", CouponType.FIXED, 3_000L, null, now.minusDays(1));

            userCouponJpaRepository.save(UserCoupon.issue(userId, validPolicy, now));

            UserCoupon used = UserCoupon.issue(userId, validPolicy, now);
            used.use(userId, 20_000L, now);
            userCouponJpaRepository.save(used);

            userCouponJpaRepository.save(UserCoupon.issue(userId, expiredPolicy, now.minusDays(2)));

            // when
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.Response>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.Response>>> response = testRestTemplate.exchange(
                MY_COUPONS_ENDPOINT, HttpMethod.GET, new HttpEntity<>(null, authHeaders()), responseType
            );

            // then
            List<CouponV1Dto.Response> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).hasSize(3),
                () -> assertThat(data).extracting(CouponV1Dto.Response::status)
                    .containsExactlyInAnyOrder(
                        CouponDisplayStatus.AVAILABLE,
                        CouponDisplayStatus.USED,
                        CouponDisplayStatus.EXPIRED
                    )
            );
        }

        @DisplayName("발급받은 쿠폰이 없으면, 200 과 함께 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoCoupons() {
            // when
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.Response>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.Response>>> response = testRestTemplate.exchange(
                MY_COUPONS_ENDPOINT, HttpMethod.GET, new HttpEntity<>(null, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("인증 헤더가 누락되면, UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // when
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.Response>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.Response>>> response = testRestTemplate.exchange(
                MY_COUPONS_ENDPOINT, HttpMethod.GET, new HttpEntity<>(null, new HttpHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
