package com.loopers.interfaces.api.coupon;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.dto.IssueCouponV1Response;
import com.loopers.interfaces.api.coupon.dto.MyCouponV1Response;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class CouponApiE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Pass1234!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long templateId;

    @BeforeEach
    void setUp() {
        userFacade.signUp(LOGIN_ID, PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@loopers.com");
        templateId = couponTemplateRepository.save(
            new CouponTemplate("10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(7))).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    @DisplayName("쿠폰을 발급하면 200 OK이고 AVAILABLE 상태로 발급되며 내 쿠폰 목록에서 조회된다")
    @Test
    void issue_thenListedInMyCoupons() {
        // when - 발급
        ResponseEntity<ApiResponse<IssueCouponV1Response>> issueRes = restTemplate.exchange(
            "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // then - 발급 응답
        assertAll(
            () -> assertThat(issueRes.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(issueRes.getBody().data().status()).isEqualTo(CouponStatus.AVAILABLE)
        );

        // when - 내 쿠폰 목록
        ResponseEntity<ApiResponse<List<MyCouponV1Response>>> listRes = restTemplate.exchange(
            "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // then - 목록에 AVAILABLE 쿠폰 1건
        List<MyCouponV1Response> coupons = listRes.getBody().data();
        assertAll(
            () -> assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(coupons).hasSize(1),
            () -> assertThat(coupons.get(0).status()).isEqualTo(CouponStatus.AVAILABLE),
            () -> assertThat(coupons.get(0).type()).isEqualTo(CouponType.RATE)
        );
    }

    @DisplayName("같은 템플릿을 두 번 발급하면 내 쿠폰 목록에 2건이 조회된다(다중 발급)")
    @Test
    void issueTwice_listsTwoCoupons() {
        ResponseEntity<ApiResponse<IssueCouponV1Response>> firstIssue = restTemplate.exchange(
            "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<IssueCouponV1Response>> secondIssue = restTemplate.exchange(
            "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {});

        ResponseEntity<ApiResponse<List<MyCouponV1Response>>> listRes = restTemplate.exchange(
            "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertAll(
            () -> assertThat(firstIssue.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(secondIssue.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(listRes.getBody().data()).hasSize(2)
        );
    }

    @DisplayName("인증 헤더 없이 발급하면 401 UNAUTHORIZED 를 반환한다")
    @Test
    void issue_returns401_whenUnauthenticated() {
        ResponseEntity<ApiResponse<IssueCouponV1Response>> response = restTemplate.exchange(
            "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(new HttpHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("존재하지 않는 템플릿으로 발급하면 404 NOT_FOUND 이고 표준 에러 응답(FAIL) 형식을 따른다")
    @Test
    void issue_returns404_whenTemplateNotFound() {
        ResponseEntity<ApiResponse<IssueCouponV1Response>> response = restTemplate.exchange(
            "/api/v1/coupons/99999/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
        );
    }

    @DisplayName("만료된 템플릿으로 발급하면 409 CONFLICT 이고 표준 에러 응답(FAIL) 형식을 따른다")
    @Test
    void issue_returns409_whenTemplateExpired() {
        Long expiredId = couponTemplateRepository.save(
            new CouponTemplate("만료 쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().minusDays(1))).getId();

        ResponseEntity<ApiResponse<IssueCouponV1Response>> response = restTemplate.exchange(
            "/api/v1/coupons/" + expiredId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
        );
    }
}
