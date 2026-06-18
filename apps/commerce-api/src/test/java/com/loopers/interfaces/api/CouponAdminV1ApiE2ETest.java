package com.loopers.interfaces.api;

import com.loopers.application.coupon.CouponAdminInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String COLLECTION = "/api-admin/v1/coupons";
    private static final String ITEM = "/api-admin/v1/coupons/{couponPolicyId}";
    private static final String ISSUES = "/api-admin/v1/coupons/{couponPolicyId}/issues";
    private static final LocalDateTime EXPIRED_AT_LOCAL = LocalDateTime.parse("2099-12-31T23:59:59");
    private static final ZonedDateTime EXPIRED_AT = EXPIRED_AT_LOCAL.atZone(ZoneId.of("Asia/Seoul"));

    private final TestRestTemplate testRestTemplate;
    private final CouponFacade couponFacade;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponFacade couponFacade,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponFacade = couponFacade;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.ADMIN_LDAP, AuthHeaders.ADMIN_LDAP_VALUE);
        return headers;
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class CreatePolicy {

        @DisplayName("유효한 요청이면, 200 과 신규 쿠폰 정책 정보(deletedAt=null)를 반환한다.")
        @Test
        void returnsCreatedPolicy_whenRequestIsValid() {
            // given
            CouponAdminV1Dto.CreateRequest request =
                new CouponAdminV1Dto.CreateRequest("신규 쿠폰", CouponType.FIXED, 5_000L, 10_000L, EXPIRED_AT_LOCAL);

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // then
            CouponAdminV1Dto.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.name()).isEqualTo("신규 쿠폰"),
                () -> assertThat(data.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(data.value()).isEqualTo(5_000L),
                () -> assertThat(data.minOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(data.deletedAt()).isNull()
            );
        }

        @DisplayName("expiredAt 을 오프셋 없는 형식(2026-12-31T23:59:59)으로 보내도, 200 과 함께 KST 기준으로 등록된다.")
        @Test
        void acceptsOffsetlessExpiredAt() {
            // given
            String body = """
                {
                  "name": "신규가입 10% 할인",
                  "type": "RATE",
                  "value": 10,
                  "minOrderAmount": 10000,
                  "expiredAt": "2026-12-31T23:59:59"
                }
                """;
            HttpHeaders headers = adminHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(body, headers), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(response.getBody().data().expiredAt())
                    .isEqualTo(ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneId.of("Asia/Seoul")))
            );
        }

        @DisplayName("정률 할인율이 100 을 초과하면, 400 과 INVALID_COUPON_VALUE 코드를 반환한다.")
        @Test
        void returnsBadRequest_whenRateValueExceedsHundred() {
            // given
            CouponAdminV1Dto.CreateRequest request =
                new CouponAdminV1Dto.CreateRequest("이상한 쿠폰", CouponType.RATE, 101L, null, EXPIRED_AT_LOCAL);

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_COUPON_VALUE")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class ListPolicies {

        @DisplayName("삭제된 정책을 포함해 전체 정책을 페이지 메타와 함께 반환한다.")
        @Test
        void returnsPagedPoliciesIncludingDeleted() {
            // given
            CouponAdminInfo deleted = couponFacade.createPolicy("폐기 쿠폰", CouponType.FIXED, 1_000L, null, EXPIRED_AT);
            couponFacade.createPolicy("유효 쿠폰", CouponType.FIXED, 2_000L, null, EXPIRED_AT);
            couponFacade.deletePolicy(deleted.id());

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.PageResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // then
            CouponAdminV1Dto.PageResponse body = response.getBody().data();
            CouponAdminV1Dto.Response deletedItem = body.content().stream()
                .filter(item -> item.id().equals(deleted.id()))
                .findFirst()
                .orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.totalElements()).isEqualTo(2L),
                () -> assertThat(body.page()).isEqualTo(0),
                () -> assertThat(body.size()).isEqualTo(20),
                () -> assertThat(deletedItem.deletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponPolicyId}")
    @Nested
    class GetPolicy {

        @DisplayName("soft-delete 된 정책 id 로 요청해도, 200 과 deletedAt 이 노출된 정보를 반환한다.")
        @Test
        void returnsPolicy_whenSoftDeleted() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("삭제될 쿠폰", CouponType.FIXED, 3_000L, null, EXPIRED_AT);
            couponFacade.deletePolicy(created.id());

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(created.id()),
                () -> assertThat(response.getBody().data().deletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 id 로 요청하면, 404 와 COUPON_POLICY_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenPolicyDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType, missingId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("COUPON_POLICY_NOT_FOUND")
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponPolicyId}")
    @Nested
    class UpdatePolicy {

        @DisplayName("메타 정보를 수정하면, 200 과 변경된 정보를 반환하고 타입·할인값은 유지된다.")
        @Test
        void returnsUpdatedPolicy_whenMetaUpdated() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);
            LocalDateTime newExpiredAt = LocalDateTime.parse("2100-01-31T23:59:59");
            CouponAdminV1Dto.UpdateRequest request =
                new CouponAdminV1Dto.UpdateRequest("변경된 쿠폰", 20_000L, newExpiredAt);

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), responseType, created.id()
            );

            // then
            CouponAdminV1Dto.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.name()).isEqualTo("변경된 쿠폰"),
                () -> assertThat(data.minOrderAmount()).isEqualTo(20_000L),
                () -> assertThat(data.type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(data.value()).isEqualTo(3_000L)
            );
        }

        @DisplayName("soft-delete 된 정책을 수정하려고 하면, 404 와 COUPON_POLICY_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenPolicyIsSoftDeleted() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("삭제될 쿠폰", CouponType.FIXED, 3_000L, null, EXPIRED_AT);
            couponFacade.deletePolicy(created.id());
            CouponAdminV1Dto.UpdateRequest request =
                new CouponAdminV1Dto.UpdateRequest("변경 시도", null, EXPIRED_AT_LOCAL);

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.Response>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("COUPON_POLICY_NOT_FOUND")
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponPolicyId}")
    @Nested
    class DeletePolicy {

        @DisplayName("정상 삭제 시, 200 을 반환하고 정책이 soft-delete 된다.")
        @Test
        void returnsOkAndSoftDeletesPolicy() {
            // given
            CouponAdminInfo created = couponFacade.createPolicy("삭제될 쿠폰", CouponType.FIXED, 3_000L, null, EXPIRED_AT);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(couponFacade.getPolicy(created.id()).deletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 id 로 삭제하면, 404 와 COUPON_POLICY_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenPolicyDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType, missingId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("COUPON_POLICY_NOT_FOUND")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponPolicyId}/issues")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("해당 정책의 발급 내역을 사용자 ID·표시 상태와 함께 페이지 메타로 반환한다.")
        @Test
        void returnsIssuedCoupons_withUserIdAndStatus() {
            // given
            CouponAdminInfo policy = couponFacade.createPolicy("발급 쿠폰", CouponType.FIXED, 3_000L, null, EXPIRED_AT);
            couponFacade.issue(1L, policy.id());
            couponFacade.issue(2L, policy.id());

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.IssuedCouponPageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.IssuedCouponPageResponse>> response = testRestTemplate.exchange(
                ISSUES, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType, policy.id()
            );

            // then
            CouponAdminV1Dto.IssuedCouponPageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.totalElements()).isEqualTo(2L),
                () -> assertThat(body.content()).extracting(CouponAdminV1Dto.IssuedCouponResponse::userId)
                    .containsExactlyInAnyOrder(1L, 2L),
                () -> assertThat(body.content()).allSatisfy(item ->
                    assertThat(item.status()).isEqualTo(CouponDisplayStatus.AVAILABLE)),
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(2L)
            );
        }

        @DisplayName("존재하지 않는 정책의 발급 내역을 조회하면, 404 와 COUPON_POLICY_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenPolicyDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.IssuedCouponPageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.IssuedCouponPageResponse>> response = testRestTemplate.exchange(
                ISSUES, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType, missingId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("COUPON_POLICY_NOT_FOUND")
            );
        }
    }
}
