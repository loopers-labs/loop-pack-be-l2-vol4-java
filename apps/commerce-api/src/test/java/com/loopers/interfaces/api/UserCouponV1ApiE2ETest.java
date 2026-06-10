package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

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

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserCouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/users/me/coupons";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> LIST_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private CouponModel saveCoupon(String name) {
        return couponJpaRepository.save(CouponModel.builder()
            .rawName(name)
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build());
    }

    private CouponModel saveExpiredCoupon(String name) {
        ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);

        return couponJpaRepository.save(CouponModel.builder()
            .rawName(name)
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(pastExpiredAt)
            .now(pastExpiredAt.minusDays(1))
            .build());
    }

    private UserCouponModel saveUserCoupon(Long userId, CouponModel coupon) {
        return userCouponJpaRepository.save(UserCouponModel.issue(userId, coupon));
    }

    private void saveUsedUserCoupon(Long userId, CouponModel coupon) {
        userCouponJpaRepository.save(UserCouponModel.builder()
            .userId(userId)
            .couponId(coupon.getId())
            .name(coupon.getName().value())
            .discountType(coupon.getType())
            .discountValue(coupon.getDiscountValue())
            .minOrderAmount(coupon.getMinOrderAmount().value())
            .expiredAt(coupon.getExpiredAt().value())
            .usedAt(ZonedDateTime.now())
            .build());
    }

    private HttpEntity<Void> memberGet(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(new HttpHeaders());
    }

    @DisplayName("내 쿠폰 목록 - GET /api/v1/users/me/coupons")
    @Nested
    class ReadMyCoupons {

        @DisplayName("정상 요청이면, 200 OK와 함께 본인 쿠폰 전체가 상태와 함께 반환된다.")
        @Test
        void returnsOk_withMyCouponsAndStatus() {
            // arrange (사용 가능·사용 완료·만료 세 상태의 쿠폰을 모두 만든다)
            UserModel user = saveUser("kylekim");
            saveUserCoupon(user.getId(), saveCoupon("사용 가능 쿠폰"));
            saveUsedUserCoupon(user.getId(), saveCoupon("사용 완료 쿠폰"));
            saveUserCoupon(user.getId(), saveExpiredCoupon("만료 쿠폰"));

            // act
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                memberGet("kylekim"),
                LIST_RESPONSE
            );

            // assert
            List<Map<String, Object>> myCoupons = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(myCoupons).hasSize(3),
                () -> assertThat(myCoupons.get(0))
                    .containsOnlyKeys("userCouponId", "name", "discountType", "discountValue", "minOrderAmount", "expiredAt", "status"),
                () -> assertThat(myCoupons)
                    .extracting(coupon -> Map.entry((String) coupon.get("name"), (String) coupon.get("status")))
                    .containsExactlyInAnyOrder(
                        Map.entry("사용 가능 쿠폰", "AVAILABLE"),
                        Map.entry("사용 완료 쿠폰", "USED"),
                        Map.entry("만료 쿠폰", "EXPIRED")
                    )
            );
        }

        @DisplayName("발급 이력이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyList() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                memberGet("kylekim"),
                LIST_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("타 회원의 쿠폰은 목록에 포함되지 않는다.")
        @Test
        void excludesOtherUsersCoupons() {
            // arrange
            UserModel owner = saveUser("owner");
            saveUser("kylekim");
            saveUserCoupon(owner.getId(), saveCoupon("타인 쿠폰"));

            // act
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                memberGet("kylekim"),
                LIST_RESPONSE
            );

            // assert
            assertThat(response.getBody().data()).isEmpty();
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                guestGet(),
                LIST_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }
    }
}
