package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateEntity;
import com.loopers.domain.coupon.CouponType;
import com.loopers.application.user.UserApplicationService;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String DEFAULT_LOGIN_ID = "couponuser1";
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private static final String ENDPOINT_COUPONS = "/api/v1/coupons";
    private static final String ENDPOINT_MY_COUPONS = "/api/v1/users/me/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponApplicationService couponApplicationService;
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final UserApplicationService userApplicationService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            CouponApplicationService couponApplicationService,
            CouponTemplateJpaRepository couponTemplateJpaRepository,
            UserApplicationService userApplicationService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponApplicationService = couponApplicationService;
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.userApplicationService = userApplicationService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createUser() {
        return userApplicationService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, "홍길동",
                LocalDate.of(1995, 1, 1), "coupon@test.com").id();
    }

    private CouponTemplateInfo createTemplate() {
        return couponApplicationService.createTemplate(
                "테스트 쿠폰", CouponType.FIXED, 1000L, 10000L,
                ZonedDateTime.now().plusDays(30)
        );
    }

    private Long createExpiredTemplate() {
        CouponTemplateEntity expiredTemplate = CouponTemplateEntity.of(
                null, "만료된 쿠폰", CouponType.FIXED, 1000L, null,
                ZonedDateTime.now().minusDays(1),
                null, null, null
        );
        return couponTemplateJpaRepository.save(CouponTemplateMapper.toJpaEntity(expiredTemplate)).getId();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, DEFAULT_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, DEFAULT_PASSWORD);
        return headers;
    }

    // ─────────────────────────────────────────────
    // POST /api/v1/coupons/{couponTemplateId}/issue
    // ─────────────────────────────────────────────

    @DisplayName("POST /api/v1/coupons/{couponTemplateId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 요청이면 201과 발급된 쿠폰을 반환한다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            createUser();
            CouponTemplateInfo template = createTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueCouponResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssueCouponResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_COUPONS + "/" + template.templateId() + "/issue",
                            HttpMethod.POST, new HttpEntity<>(userHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().couponId()).isNotNull();
            assertThat(response.getBody().data().status()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("인증 헤더 없이 요청하면 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // arrange
            CouponTemplateInfo template = createTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_COUPONS + "/" + template.templateId() + "/issue",
                            HttpMethod.POST, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿 ID로 발급 요청하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // arrange
            createUser();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_COUPONS + "/999/issue",
                            HttpMethod.POST, new HttpEntity<>(userHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰 템플릿으로 발급 요청하면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenTemplateIsExpired() {
            // arrange
            createUser();
            Long expiredTemplateId = createExpiredTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_COUPONS + "/" + expiredTemplateId + "/issue",
                            HttpMethod.POST, new HttpEntity<>(userHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/users/me/coupons
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("내 쿠폰 목록 조회 시 200과 발급된 쿠폰 목록을 반환한다.")
        @Test
        void returnsMyCoupons_whenUserHasCoupons() {
            // arrange
            Long userId = createUser();
            CouponTemplateInfo template = createTemplate();
            couponApplicationService.issueCoupon(userId, template.templateId());

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<CouponV1Dto.MyCouponResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<CouponV1Dto.MyCouponResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_MY_COUPONS + "?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(userHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
            assertThat(response.getBody().data().content().get(0).couponId()).isNotNull();
            assertThat(response.getBody().data().content().get(0).status()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("인증 헤더 없이 요청하면 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_MY_COUPONS,
                            HttpMethod.GET, HttpEntity.EMPTY, type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
