package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponType;
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

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String ENDPOINT_COUPONS = "/api-admin/v1/coupons";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class CreateCoupon {

        @DisplayName("어드민 헤더와 유효한 정액 쿠폰 정보가 주어지면, 201 CREATED와 생성된 쿠폰 정보를 반환한다.")
        @Test
        void returnsCreatedCoupon_whenAdminHeaderAndFixedCouponRequestAreProvided() {
            // arrange
            CouponAdminV1Dto.CreateCouponRequest request = new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 2,000원 할인",
                CouponType.FIXED,
                2_000L,
                10_000L,
                EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = createCoupon(request, adminHeaders());

            // assert
            CouponAdminV1Dto.CouponResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.name()).isEqualTo("1주년 2,000원 할인"),
                () -> assertThat(data.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(data.discountValue()).isEqualTo(2_000L),
                () -> assertThat(data.minimumOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(data.expiredAt().toInstant())
                    .isEqualTo(EXPIRED_AT.toInstant()),
                () -> assertThat(data.createdAt()).isNotNull(),
                () -> assertThat(data.updatedAt()).isNotNull(),
                () -> assertThat(data.deletedAt()).isNull()
            );
        }

        @DisplayName("어드민 헤더와 유효한 정률 쿠폰 정보가 주어지면, 201 CREATED와 생성된 쿠폰 정보를 반환한다.")
        @Test
        void returnsCreatedCoupon_whenAdminHeaderAndRateCouponRequestAreProvided() {
            // arrange
            CouponAdminV1Dto.CreateCouponRequest request = new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = createCoupon(request, adminHeaders());

            // assert
            CouponAdminV1Dto.CouponResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.name()).isEqualTo("1주년 10% 할인"),
                () -> assertThat(data.type()).isEqualTo(CouponType.RATE),
                () -> assertThat(data.discountValue()).isEqualTo(10L),
                () -> assertThat(data.minimumOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(data.expiredAt().toInstant())
                    .isEqualTo(EXPIRED_AT.toInstant()),
                () -> assertThat(data.createdAt()).isNotNull(),
                () -> assertThat(data.updatedAt()).isNotNull(),
                () -> assertThat(data.deletedAt()).isNull()
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // arrange
            CouponAdminV1Dto.CreateCouponRequest request = new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 2,000원 할인",
                CouponType.FIXED,
                2_000L,
                10_000L,
                EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = createCoupon(request, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> createCoupon(
        CouponAdminV1Dto.CreateCouponRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPONS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP);
        return headers;
    }
}
