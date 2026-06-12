package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserDto;
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

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_MY_COUPONS = "/api/v1/users/me/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponFacade couponFacade;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponFacade couponFacade,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponFacade = couponFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {
        @DisplayName("인증된 회원이 쿠폰 발급을 요청하면, 사용 가능한 쿠폰을 발급한다.")
        @Test
        void issuesCoupon_whenUserIsAuthenticated() {
            // arrange
            signup("user1234", "abc123!?");
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                ZonedDateTime.now().plusDays(7)
            );

            // act
            ResponseEntity<ApiResponse<CouponDto.Issued.Response>> response =
                testRestTemplate.exchange(
                    "/api/v1/coupons/" + coupon.id() + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    issuedCouponResponseType()
                );

            // assert
            CouponDto.Issued.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.couponId()).isEqualTo(coupon.id()),
                () -> assertThat(data.userLoginId()).isEqualTo("user1234"),
                () -> assertThat(data.status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenCredentialHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    "/api/v1/coupons/1/issue",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {
        @DisplayName("인증된 회원이면, 내 쿠폰 목록을 조회한다.")
        @Test
        void returnsMyCoupons_whenUserIsAuthenticated() {
            // arrange
            signup("user1234", "abc123!?");
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                ZonedDateTime.now().plusDays(7)
            );
            couponFacade.issueCoupon(coupon.id(), "user1234", ZonedDateTime.now());

            // act
            ResponseEntity<ApiResponse<List<CouponDto.Issued.Response>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_MY_COUPONS,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    issuedCouponListResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).couponId()).isEqualTo(coupon.id()),
                () -> assertThat(response.getBody().data().get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }

    private void signup(String loginId, String password) {
        UserDto.Register.V1.Request request = new UserDto.Register.V1.Request(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<CouponDto.Issued.Response>> issuedCouponResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<CouponDto.Issued.Response>>> issuedCouponListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<Void>> voidResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
