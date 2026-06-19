package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.coupon.domain.UserCouponStatus;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.shared.presentation.PageResponse;
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
    private static final ZonedDateTime PAST_EXPIRED_AT = ZonedDateTime.parse("2026-01-01T00:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final TestRestTemplate testRestTemplate;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponAdminV1ApiE2ETest(
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

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {

        @DisplayName("어드민 헤더와 존재하는 쿠폰 ID가 주어지면, 200 OK와 해당 쿠폰 정보를 반환한다.")
        @Test
        void returnsCoupon_whenCouponExists() {
            // arrange
            CouponAdminV1Dto.CreateCouponRequest request = new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 2,000원 할인",
                CouponType.FIXED,
                2_000L,
                10_000L,
                EXPIRED_AT
            );
            Long couponId = createCoupon(request, adminHeaders()).getBody().data().id();

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = getCoupon(couponId, adminHeaders());

            // assert
            CouponAdminV1Dto.CouponResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(couponId),
                () -> assertThat(data.name()).isEqualTo("1주년 2,000원 할인"),
                () -> assertThat(data.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(data.discountValue()).isEqualTo(2_000L),
                () -> assertThat(data.minimumOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(data.expiredAt().toInstant()).isEqualTo(EXPIRED_AT.toInstant())
            );
        }

        @DisplayName("어드민 헤더와 존재하지 않는 쿠폰 ID가 주어지면, 404 NOT FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = getCoupon(999_999L, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = getCoupon(1L, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetCoupons {

        @DisplayName("어드민 헤더가 주어지면, 200 OK와 등록된 쿠폰 목록을 최신순으로 반환한다.")
        @Test
        void returnsCouponPage_whenAdminHeaderIsProvided() {
            // arrange
            createCoupon(new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 2,000원 할인", CouponType.FIXED, 2_000L, 10_000L, EXPIRED_AT
            ), adminHeaders());
            createCoupon(new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 10% 할인", CouponType.RATE, 10L, 10_000L, EXPIRED_AT
            ), adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response = getCoupons(0, 20, adminHeaders());

            // assert
            PageResponse<CouponAdminV1Dto.CouponResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(2),
                () -> assertThat(data.content()).hasSize(2),
                () -> assertThat(data.content().get(0).name()).isEqualTo("1주년 10% 할인"),
                () -> assertThat(data.content().get(1).name()).isEqualTo("1주년 2,000원 할인")
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response = getCoupons(0, 20, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetCouponIssues {

        @DisplayName("어드민 헤더와 발급 내역이 있는 쿠폰 ID가 주어지면, 200 OK와 발급 내역을 최신순으로 반환한다.")
        @Test
        void returnsIssuePage_whenCouponHasIssues() {
            // arrange
            CouponTemplate couponTemplate = createCouponTemplate("1주년 2,000원 할인", EXPIRED_AT);
            issueTo(101L, couponTemplate);
            UserCoupon latest = issueTo(102L, couponTemplate);

            // act
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponIssueResponse>>> response =
                getCouponIssues(couponTemplate.getId(), 0, 20, adminHeaders());

            // assert
            PageResponse<CouponAdminV1Dto.CouponIssueResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(2),
                () -> assertThat(data.content()).hasSize(2),
                () -> assertThat(data.content().get(0).userCouponId()).isEqualTo(latest.getId()),
                () -> assertThat(data.content().get(0).userId()).isEqualTo(102L),
                () -> assertThat(data.content().get(0).status()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(data.content().get(0).issuedAt()).isNotNull(),
                () -> assertThat(data.content().get(0).usedAt()).isNull(),
                () -> assertThat(data.content().get(1).userId()).isEqualTo(101L)
            );
        }

        @DisplayName("발급분이 만료일을 지났으면, display 상태로 EXPIRED를 반환한다.")
        @Test
        void returnsExpiredDisplayStatus_whenIssuedCouponIsPastExpiration() {
            // arrange
            CouponTemplate expiredTemplate = createCouponTemplate("1주년 2,000원 할인", PAST_EXPIRED_AT);
            issueTo(101L, expiredTemplate);

            // act
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponIssueResponse>>> response =
                getCouponIssues(expiredTemplate.getId(), 0, 20, adminHeaders());

            // assert
            PageResponse<CouponAdminV1Dto.CouponIssueResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content()).hasSize(1),
                () -> assertThat(data.content().get(0).status()).isEqualTo(UserCouponStatus.EXPIRED)
            );
        }

        @DisplayName("어드민 헤더와 존재하지 않는 쿠폰 ID가 주어지면, 404 NOT FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponIssueResponse>>> response =
                getCouponIssues(999_999L, 0, 20, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponIssueResponse>>> response =
                getCouponIssues(1L, 0, 20, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        @DisplayName("어드민 헤더와 유효한 수정 요청이 주어지면, 200 OK와 수정된 쿠폰 정보를 반환한다.")
        @Test
        void updatesCoupon_whenAdminHeaderAndValidRequest() {
            // arrange
            Long couponId = createCoupon(new CouponAdminV1Dto.CreateCouponRequest(
                "1주년 2,000원 할인", CouponType.FIXED, 2_000L, 10_000L, EXPIRED_AT
            ), adminHeaders()).getBody().data().id();
            CouponAdminV1Dto.UpdateCouponRequest request = new CouponAdminV1Dto.UpdateCouponRequest(
                "1주년 10% 할인", CouponType.RATE, 10L, 20_000L, EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = updateCoupon(couponId, request, adminHeaders());

            // assert
            CouponAdminV1Dto.CouponResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(couponId),
                () -> assertThat(data.name()).isEqualTo("1주년 10% 할인"),
                () -> assertThat(data.type()).isEqualTo(CouponType.RATE),
                () -> assertThat(data.discountValue()).isEqualTo(10L),
                () -> assertThat(data.minimumOrderAmount()).isEqualTo(20_000L)
            );
        }

        @DisplayName("어드민 헤더와 존재하지 않는 쿠폰 ID가 주어지면, 404 NOT FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // arrange
            CouponAdminV1Dto.UpdateCouponRequest request = new CouponAdminV1Dto.UpdateCouponRequest(
                "1주년 10% 할인", CouponType.RATE, 10L, 20_000L, EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = updateCoupon(999_999L, request, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더와 쿠폰 이름이 빈 요청이 주어지면, 400 BAD REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            CouponAdminV1Dto.UpdateCouponRequest request = new CouponAdminV1Dto.UpdateCouponRequest(
                " ", CouponType.RATE, 10L, 20_000L, EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = updateCoupon(1L, request, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // arrange
            CouponAdminV1Dto.UpdateCouponRequest request = new CouponAdminV1Dto.UpdateCouponRequest(
                "1주년 10% 할인", CouponType.RATE, 10L, 20_000L, EXPIRED_AT
            );

            // act
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = updateCoupon(1L, request, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> updateCoupon(Long couponId, CouponAdminV1Dto.UpdateCouponRequest request, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPONS + "/" + couponId,
            HttpMethod.PUT,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteCoupon {

        @DisplayName("어드민 헤더와 존재하는 쿠폰 ID가 주어지면 200 OK를 반환하고, 이후 상세 조회에서 제외한다.")
        @Test
        void deletesCoupon_whenAdminHeaderAndCouponIdExist() {
            // arrange
            CouponTemplate couponTemplate = createCouponTemplate("1주년 2,000원 할인", EXPIRED_AT);

            // act
            ResponseEntity<ApiResponse<Object>> deleteResponse = deleteCoupon(couponTemplate.getId(), adminHeaders());
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> getResponse = getCoupon(couponTemplate.getId(), adminHeaders());

            // assert
            assertAll(
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("삭제해도 이미 발급된 사용자 쿠폰은 영향받지 않는다.")
        @Test
        void keepsIssuedUserCoupons_whenTemplateIsDeleted() {
            // arrange
            CouponTemplate couponTemplate = createCouponTemplate("1주년 2,000원 할인", EXPIRED_AT);
            UserCoupon issued = issueTo(101L, couponTemplate);

            // act
            ResponseEntity<ApiResponse<Object>> deleteResponse = deleteCoupon(couponTemplate.getId(), adminHeaders());

            // assert
            UserCoupon survived = userCouponRepository.findById(issued.getId()).orElseThrow();
            assertAll(
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(survived.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("어드민 헤더와 존재하지 않는 쿠폰 ID가 주어지면, 404 NOT FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<Object>> response = deleteCoupon(999_999L, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("어드민 헤더가 없으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Object>> response = deleteCoupon(1L, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<ApiResponse<Object>> deleteCoupon(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPONS + "/" + couponId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponIssueResponse>>> getCouponIssues(Long couponId, int page, int size, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponIssueResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPONS + "/" + couponId + "/issues?page=" + page + "&size=" + size,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
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

    private UserCoupon issueTo(Long userId, CouponTemplate couponTemplate) {
        return userCouponRepository.save(UserCoupon.issue(userId, couponTemplate.getId(), couponTemplate));
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> getCoupon(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPONS + "/" + couponId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> getCoupons(int page, int size, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_COUPONS + "?page=" + page + "&size=" + size,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
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
