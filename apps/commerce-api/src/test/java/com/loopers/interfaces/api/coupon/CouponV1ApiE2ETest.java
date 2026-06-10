package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.CouponExpiry;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Test1234!";
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveDefaultUser() {
        return userRepository.save(new UserModel(
                new UserId(LOGIN_ID),
                new Password(passwordEncoder.encode(LOGIN_PW)),
                new Name("테스트유저"),
                new BirthDay("1990-01-01"),
                new Email("test@loopers.com"),
                UserRole.USER
        ));
    }

    private CouponModel saveValidCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "10% 할인 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(FUTURE)
        ));
    }

    private CouponModel saveExpiredCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "만료 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(PAST)
        ));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("인증 헤더 없이 요청하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            CouponModel coupon = saveValidCoupon();
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response =
                    testRestTemplate.exchange("/api/v1/coupons/" + coupon.getId() + "/issue", HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 쿠폰이면, 201 CREATED와 발급 정보를 반환한다.")
        @Test
        void returnsUserCoupon_whenCouponIsValid() {
            saveDefaultUser();
            CouponModel coupon = saveValidCoupon();
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response =
                    testRestTemplate.exchange("/api/v1/coupons/" + coupon.getId() + "/issue", HttpMethod.POST, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().couponId()).isEqualTo(coupon.getId());
            assertThat(response.getBody().data().status()).isEqualTo("발급");
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            saveDefaultUser();
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response =
                    testRestTemplate.exchange("/api/v1/coupons/999/issue", HttpMethod.POST, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenCouponIsExpired() {
            saveDefaultUser();
            CouponModel coupon = saveExpiredCoupon();
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response =
                    testRestTemplate.exchange("/api/v1/coupons/" + coupon.getId() + "/issue", HttpMethod.POST, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("인증 헤더 없이 요청하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response =
                    testRestTemplate.exchange("/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("발급된 쿠폰이 있으면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsCoupons_whenUserHasCoupons() {
            saveDefaultUser();
            CouponModel coupon = saveValidCoupon();
            testRestTemplate.exchange("/api/v1/coupons/" + coupon.getId() + "/issue", HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class);
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response =
                    testRestTemplate.exchange("/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).hasSize(1);
        }

        @DisplayName("발급된 쿠폰이 없으면, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenUserHasNoCoupons() {
            saveDefaultUser();
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> response =
                    testRestTemplate.exchange("/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isEmpty();
        }
    }
}
