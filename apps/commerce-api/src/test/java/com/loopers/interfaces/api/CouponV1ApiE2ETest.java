package com.loopers.interfaces.api;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.money.Money;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_MY_COUPONS = "/api/v1/users/me/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void signup(String loginId, String password) {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            loginId, password, "김민우", LocalDate.of(1990, 1, 1), loginId + "@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private Coupon saveTemplate() {
        return couponJpaRepository.save(new Coupon(
            "10% 할인", new Discount(CouponType.RATE, 10L),
            new Money(BigDecimal.valueOf(10000)), LocalDateTime.of(2099, 12, 31, 23, 59, 59)
        ));
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class Issue {
        @DisplayName("유효한 인증으로 발급 요청하면, 쿠폰이 발급되고 AVAILABLE 상태로 저장된다.")
        @Test
        void issuesCoupon_whenRequestIsValid() {
            // arrange
            signup("minwoo01", "Passw0rd!");
            Coupon template = saveTemplate();
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssueResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + template.getId() + "/issue", HttpMethod.POST,
                new HttpEntity<>(null, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().couponId()).isEqualTo(template.getId()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(userCouponJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 쿠폰으로 발급 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            signup("minwoo01", "Passw0rd!");
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssueResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/999/issue", HttpMethod.POST,
                new HttpEntity<>(null, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenHeadersAreMissing() {
            // arrange
            Coupon template = saveTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssueResponse>> response = testRestTemplate.exchange(
                "/api/v1/coupons/" + template.getId() + "/issue", HttpMethod.POST,
                new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {
        @DisplayName("발급받은 쿠폰 목록을 상태와 함께 반환한다.")
        @Test
        void returnsMyCoupons() {
            // arrange
            signup("minwoo01", "Passw0rd!");
            Coupon template = saveTemplate();
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");
            testRestTemplate.exchange("/api/v1/coupons/" + template.getId() + "/issue", HttpMethod.POST,
                new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueResponse>>() {});

            // act
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.MyCouponResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.MyCouponResponse>>> response = testRestTemplate.exchange(
                ENDPOINT_MY_COUPONS, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).couponId()).isEqualTo(template.getId()),
                () -> assertThat(response.getBody().data().get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }
}
