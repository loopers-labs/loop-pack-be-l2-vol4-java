package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.domain.CouponType;
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

    private static final String ENDPOINT = "/api/v1/admin/coupons";
    private static final String ADMIN_HEADER = "X-Loopers-Admin-Id";
    private static final ZonedDateTime EXPIRES = ZonedDateTime.parse("2030-12-31T23:59:59+09:00");

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponAdminV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ADMIN_HEADER, "admin-1");
        return headers;
    }

    private CouponAdminV1Request.Create createRequest() {
        return new CouponAdminV1Request.Create("신규가입 3천원", CouponType.FIXED, 3_000L, 10_000L, EXPIRES);
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> createAsAdmin(CouponAdminV1Request.Create request) {
        ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type);
    }

    private Long createCoupon() {
        return createAsAdmin(createRequest()).getBody().data().id();
    }

    @DisplayName("POST /api/v1/admin/coupons")
    @Nested
    class Create {

        @Test
        @DisplayName("admin 헤더와 유효한 요청으로 등록하면 200 과 템플릿 정보를 반환한다")
        void givenAdminAndValidRequest_whenCreate_thenReturnsDetail() {
            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> response = createAsAdmin(createRequest());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 3천원"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(3_000L),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualTo(10_000L)
            );
        }

        @Test
        @DisplayName("admin 헤더가 없으면 401 을 반환한다")
        void givenNoAdminHeader_whenCreate_thenReturnsUnauthorized() {
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("정률 값이 100 을 초과하면 400 을 반환한다")
        void givenRateOver100_whenCreate_thenReturnsBadRequest() {
            CouponAdminV1Request.Create request =
                    new CouponAdminV1Request.Create("잘못된 정률", CouponType.RATE, 150L, null, EXPIRES);

            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> response = createAsAdmin(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/admin/coupons/{couponId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("couponId 로 단건을 조회하면 200 과 상세를 반환한다")
        void givenCoupon_whenGet_thenReturnsDetail() {
            Long couponId = createCoupon();

            ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(couponId)
            );
        }

        @Test
        @DisplayName("존재하지 않는 couponId 면 404 를 반환한다")
        void givenMissingCoupon_whenGet_thenReturnsNotFound() {
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> response = testRestTemplate.exchange(
                    ENDPOINT + "/99999", HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/admin/coupons (페이지)")
    @Nested
    class GetPage {

        @Test
        @DisplayName("페이지로 목록을 조회하면 200 과 컨텐츠/총개수를 반환한다")
        void givenCoupons_whenGetPage_thenReturnsPage() {
            createCoupon();
            createCoupon();

            ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Page>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Response.Page>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2)
            );
        }
    }

    @DisplayName("PUT /api/v1/admin/coupons/{couponId}")
    @Nested
    class Update {

        @Test
        @DisplayName("유효한 요청으로 수정하면 200 과 변경된 정보를 반환한다")
        void givenValidRequest_whenUpdate_thenReturnsUpdatedDetail() {
            Long couponId = createCoupon();
            CouponAdminV1Request.Update request =
                    new CouponAdminV1Request.Update("정률 15%", CouponType.RATE, 15L, 20_000L, EXPIRES);

            ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(15L)
            );
        }
    }

    @DisplayName("DELETE /api/v1/admin/coupons/{couponId}")
    @Nested
    class Delete {

        @Test
        @DisplayName("삭제하면 200 을 반환하고 이후 조회 시 404 가 된다")
        void givenCoupon_whenDelete_thenSoftDeletedAndNotFound() {
            Long couponId = createCoupon();

            ParameterizedTypeReference<ApiResponse<Void>> voidType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> deleteResponse = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), voidType);

            ParameterizedTypeReference<ApiResponse<CouponAdminV1Response.Detail>> detailType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Response.Detail>> getResponse = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.GET, new HttpEntity<>(adminHeaders()), detailType);

            assertAll(
                    () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }
}
