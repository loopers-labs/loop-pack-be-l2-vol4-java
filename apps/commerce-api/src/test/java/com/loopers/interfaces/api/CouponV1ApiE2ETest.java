package com.loopers.interfaces.api;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
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

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @Autowired
    public CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        UserModel user = userJpaRepository.save(
            new UserModel("tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        this.userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, "tester01");
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    private Coupon persistCoupon() {
        return couponJpaRepository.save(
            new Coupon("10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30)));
    }

    private ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> issue(Long couponId) {
        ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
            new HttpEntity<>(authHeaders()), responseType
        );
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class Issue {

        @DisplayName("정상 발급하면, 2xx 응답과 AVAILABLE 상태의 쿠폰을 반환한다.")
        @Test
        void issuesCoupon() {
            // arrange
            Coupon coupon = persistCoupon();

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = issue(coupon.getId());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("10% 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo("RATE"),
                () -> assertThat(response.getBody().data().status()).isEqualTo("AVAILABLE"),
                () -> assertThat(response.getBody().data().couponId()).isEqualTo(coupon.getId())
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenCouponMissing() {
            // act
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = issue(99999L);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 쿠폰이면, 404 Not Found 응답을 반환한다. (soft delete 는 미존재와 동일하게 취급)")
        @Test
        void returnsNotFound_whenCouponDeleted() {
            // arrange
            Coupon coupon = persistCoupon();
            coupon.delete();
            couponJpaRepository.save(coupon);

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = issue(coupon.getId());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("보유 쿠폰을 AVAILABLE / USED / EXPIRED 상태와 함께 반환한다.")
        @Test
        void returnsCouponsWithStatus() {
            // arrange
            Coupon coupon = persistCoupon();
            ZonedDateTime now = ZonedDateTime.now();
            userCouponJpaRepository.save(coupon.issueTo(userId, now)); // AVAILABLE
            UserCoupon used = coupon.issueTo(userId, now);
            used.use(userId, now);
            userCouponJpaRepository.save(used); // USED
            userCouponJpaRepository.save(
                new UserCoupon(userId, coupon.getId(), coupon.snapshot(), now.minusDays(10), now.minusDays(1))); // EXPIRED (파생)

            // act
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data()).hasSize(3),
                () -> assertThat(response.getBody().data())
                    .extracting(CouponV1Dto.UserCouponResponse::status)
                    .containsExactlyInAnyOrder("AVAILABLE", "USED", "EXPIRED")
            );
        }

        @DisplayName("인증 헤더가 없으면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
