package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Admin이 쿠폰 템플릿을 관리하고 사용자는 쿠폰 발급 요청을 접수한 뒤 상태를 조회한다.")
    @Test
    void managesTemplateAndRequestsCouponIssue() {
        CouponAdminDto.UpsertCouponTemplateRequest createRequest = new CouponAdminDto.UpsertCouponTemplateRequest(
            "신규가입 10% 할인",
            CouponType.RATE,
            10L,
            10_000L,
            null,
            1,
            ZonedDateTime.now().plusDays(1)
        );

        ResponseEntity<ApiResponse<CouponAdminDto.CouponTemplateResponse>> createResponse = exchange(
            "/api-admin/v1/coupons",
            HttpMethod.POST,
            new HttpEntity<>(createRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        Long couponTemplateId = createResponse.getBody().data().couponId();

        CouponAdminDto.UpsertCouponTemplateRequest updateRequest = new CouponAdminDto.UpsertCouponTemplateRequest(
            "신규가입 15% 할인",
            CouponType.RATE,
            15L,
            10_000L,
            null,
            1,
            ZonedDateTime.now().plusDays(2)
        );
        ResponseEntity<ApiResponse<CouponAdminDto.CouponTemplateResponse>> updateResponse = exchange(
            "/api-admin/v1/coupons/" + couponTemplateId,
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> issueResponse = exchange(
            "/api/v1/coupons/" + couponTemplateId + "/issue",
            HttpMethod.POST,
            new HttpEntity<>(userHeaders("user1")),
            new ParameterizedTypeReference<>() {}
        );
        Long requestId = issueResponse.getBody().data().requestId();

        ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> issueRequestResponse = exchange(
            "/api/v1/coupons/issues/" + requestId,
            HttpMethod.GET,
            new HttpEntity<>(userHeaders("user1")),
            new ParameterizedTypeReference<>() {}
        );

        ResponseEntity<ApiResponse<PageResponse<CouponAdminDto.CouponIssueResponse>>> issuesResponse = exchange(
            "/api-admin/v1/coupons/" + couponTemplateId + "/issues?page=0&size=20",
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        ResponseEntity<ApiResponse<Void>> deleteResponse = exchange(
            "/api-admin/v1/coupons/" + couponTemplateId,
            HttpMethod.DELETE,
            new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        ResponseEntity<ApiResponse<CouponAdminDto.CouponTemplateResponse>> detailResponse = exchange(
            "/api-admin/v1/coupons/" + couponTemplateId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertAll(
            () -> assertTrue(createResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(updateResponse.getBody().data().name()).isEqualTo("신규가입 15% 할인"),
            () -> assertThat(issueResponse.getBody().data().status()).isEqualTo(CouponIssueRequestStatus.PENDING),
            () -> assertThat(issueRequestResponse.getBody().data().requestId()).isEqualTo(requestId),
            () -> assertThat(issueRequestResponse.getBody().data().status()).isEqualTo(CouponIssueRequestStatus.PENDING),
            () -> assertThat(issuesResponse.getBody().data().pageInfo().totalElements()).isZero(),
            () -> assertTrue(deleteResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(detailResponse.getBody().data().active()).isFalse()
        );
    }

    private <T> ResponseEntity<T> exchange(
        String url,
        HttpMethod method,
        HttpEntity<?> request,
        ParameterizedTypeReference<T> responseType
    ) {
        return testRestTemplate.exchange(url, method, request, responseType);
    }

    private HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.LOGIN_ID, userId);
        headers.add(HeaderValidator.LOGIN_PW, "password");
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.ADMIN_LDAP, "loopers.admin");
        return headers;
    }
}
