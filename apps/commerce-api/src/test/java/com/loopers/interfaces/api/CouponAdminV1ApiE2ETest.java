package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.HashMap;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.loopers.domain.coupon.DiscountType;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api-admin/v1/coupons";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> adminJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> jsonRequestWithoutAdmin(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    @DisplayName("쿠폰 템플릿 등록 - POST /api-admin/v1/coupons")
    @Nested
    class CreateCoupon {

        private final ZonedDateTime futureExpiredAt = ZonedDateTime.now().plusDays(7);

        @DisplayName("정상 요청이면, 201 Created와 함께 couponId가 응답 본문에 담겨 반환된다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("신규 가입 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("couponId"),
                () -> assertThat(response.getBody().data().get("couponId")).isNotNull(),
                () -> assertThat(couponJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("최소 주문 금액을 생략한 정상 요청이면, 201 Created로 쿠폰 템플릿이 생성된다.")
        @Test
        void returnsCreated_whenMinOrderAmountIsOmitted() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("조건 없는 쿠폰", DiscountType.FIXED, 3_000, null, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("couponId")).isNotNull(),
                () -> assertThat(couponJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("신규 가입 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("할인 타입이 허용되지 않는 값이면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenDiscountTypeIsNotAllowed() {
            // arrange
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "잘못된 타입 쿠폰");
            requestBody.put("discountType", "PERCENT");
            requestBody.put("discountValue", 10);
            requestBody.put("expiredAt", futureExpiredAt.toString());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("쿠폰 이름이 100자를 초과하면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("쿠".repeat(101), DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenRateValueExceedsMax() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("정률 쿠폰", DiscountType.RATE, 101, null, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("만료 시각이 현재 시각 이전이면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenExpiredAtIsPast() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("만료된 쿠폰", DiscountType.FIXED, 5_000, null, ZonedDateTime.now().minusDays(1));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }
    }
}
