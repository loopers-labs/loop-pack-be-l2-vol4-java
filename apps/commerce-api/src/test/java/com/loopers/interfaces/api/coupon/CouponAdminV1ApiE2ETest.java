package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.application.user.UserApplicationService;
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
class CouponAdminV1ApiE2ETest {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";
    private static final String ENDPOINT_ADMIN = "/api-admin/v1/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponApplicationService couponApplicationService;
    private final UserApplicationService userApplicationService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponAdminV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            CouponApplicationService couponApplicationService,
            UserApplicationService userApplicationService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponApplicationService = couponApplicationService;
        this.userApplicationService = userApplicationService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    private CouponTemplateInfo createTemplate(String name) {
        return couponApplicationService.createTemplate(
                name, CouponType.FIXED, 1000L, 10000L,
                ZonedDateTime.now().plusDays(30)
        );
    }

    // ─────────────────────────────────────────────
    // POST /api-admin/v1/coupons
    // ─────────────────────────────────────────────

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class CreateTemplate {

        @DisplayName("유효한 요청이면 201과 생성된 쿠폰 템플릿을 반환한다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            CouponAdminV1Dto.CreateTemplateRequest request = new CouponAdminV1Dto.CreateTemplateRequest(
                    "신규 가입 쿠폰", CouponType.FIXED, 1000L, 10000L,
                    ZonedDateTime.now().plusDays(30)
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.TemplateDetailResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateDetailResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().couponTemplateId()).isNotNull();
            assertThat(response.getBody().data().name()).isEqualTo("신규 가입 쿠폰");
            assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED);
            assertThat(response.getBody().data().updatedAt()).isNotNull();
        }

        @DisplayName("Admin 헤더 없이 요청하면 403을 반환한다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponAdminV1Dto.CreateTemplateRequest request = new CouponAdminV1Dto.CreateTemplateRequest(
                    "신규 가입 쿠폰", CouponType.FIXED, 1000L, 10000L,
                    ZonedDateTime.now().plusDays(30)
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN,
                            HttpMethod.POST, new HttpEntity<>(request), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/coupons
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetTemplates {

        @DisplayName("등록된 템플릿 수만큼 목록을 반환한다.")
        @Test
        void returnsTemplateList_withRegisteredTemplates() {
            // arrange
            createTemplate("쿠폰 A");
            createTemplate("쿠폰 B");

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<CouponAdminV1Dto.TemplateListResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<CouponAdminV1Dto.TemplateListResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/coupons/{couponTemplateId}
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/coupons/{couponTemplateId}")
    @Nested
    class GetTemplate {

        @DisplayName("존재하는 쿠폰 템플릿 조회 시 200과 updatedAt 포함 상세 정보를 반환한다.")
        @Test
        void returnsTemplate_whenTemplateExists() {
            // arrange
            CouponTemplateInfo template = createTemplate("신규 가입 쿠폰");

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.TemplateDetailResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateDetailResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + template.templateId(),
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().couponTemplateId()).isEqualTo(template.templateId());
            assertThat(response.getBody().data().updatedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿 조회 시 404를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/999",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // PUT /api-admin/v1/coupons/{couponTemplateId}
    // ─────────────────────────────────────────────

    @DisplayName("PUT /api-admin/v1/coupons/{couponTemplateId}")
    @Nested
    class UpdateTemplate {

        @DisplayName("name, minOrderAmount, expiredAt 수정 시 200과 수정된 템플릿을 반환한다.")
        @Test
        void returnsUpdatedTemplate_whenRequestIsValid() {
            // arrange
            CouponTemplateInfo template = createTemplate("기존 쿠폰");
            CouponAdminV1Dto.UpdateTemplateRequest request = new CouponAdminV1Dto.UpdateTemplateRequest(
                    "수정된 쿠폰", 20000L, ZonedDateTime.now().plusDays(60)
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.TemplateDetailResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.TemplateDetailResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + template.templateId(),
                            HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("수정된 쿠폰");
            assertThat(response.getBody().data().minOrderAmount()).isEqualTo(20000L);
        }
    }

    // ─────────────────────────────────────────────
    // DELETE /api-admin/v1/coupons/{couponTemplateId}
    // ─────────────────────────────────────────────

    @DisplayName("DELETE /api-admin/v1/coupons/{couponTemplateId}")
    @Nested
    class DeleteTemplate {

        @DisplayName("존재하는 쿠폰 템플릿 삭제 시 200을 반환한다.")
        @Test
        void returnsOk_whenTemplateExists() {
            // arrange
            CouponTemplateInfo template = createTemplate("삭제할 쿠폰");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + template.templateId(),
                            HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿 삭제 시 404를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/999",
                            HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/coupons/{couponTemplateId}/issues
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/coupons/{couponTemplateId}/issues")
    @Nested
    class GetTemplateIssues {

        @DisplayName("발급된 쿠폰 내역을 페이지네이션으로 반환한다.")
        @Test
        void returnsIssueHistory_whenCouponsAreIssued() {
            // arrange
            CouponTemplateInfo template = createTemplate("발급 쿠폰");
            String userId = userApplicationService.signup("issueuser", "Test1234!", "홍길동",
                    LocalDate.of(1995, 1, 1), "issue@test.com").id();
            couponApplicationService.issueCoupon(userId, template.templateId());

            // act
            ParameterizedTypeReference<ApiResponse<PageResult<CouponAdminV1Dto.IssueHistoryResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<CouponAdminV1Dto.IssueHistoryResponse>>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_ADMIN + "/" + template.templateId() + "/issues?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
        }
    }
}
