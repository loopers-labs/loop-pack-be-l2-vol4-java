package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String ADMIN = "/api-admin/v1/coupons";
    private static final String MY_COUPONS = "/api/v1/users/me/coupons";
    private static final String PASSWORD = "Abcd123!";

    private final TestRestTemplate testRestTemplate;
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            CouponTemplateJpaRepository couponTemplateJpaRepository,
            UserJpaRepository userJpaRepository,
            PasswordEncoder passwordEncoder,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void createUser(String loginId) {
        userJpaRepository.save(new UserModel(
                new LoginId(loginId),
                new Name("유저"),
                new BirthDate(LocalDate.of(1999, 1, 1)),
                new Email(loginId + "@loopers.com"),
                EncodedPassword.create(passwordEncoder, PASSWORD)
        ));
    }

    private Long saveTemplate(String name, DiscountType type, long value, int validDays) {
        return couponTemplateJpaRepository.save(
                CouponTemplate.create(name, DiscountPolicy.of(type, value), validDays)).getId();
    }

    private static HttpHeaders adminHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private static HttpHeaders loginHeader(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        if (loginId != null) {
            headers.add("X-Loopers-LoginId", loginId);
            headers.add("X-Loopers-LoginPw", PASSWORD);
        }
        return headers;
    }

    private static HttpHeaders empty() {
        return new HttpHeaders();
    }

    @DisplayName("대고객 POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class Issue {

        @DisplayName("로그인 사용자가 발급하면 200 과 AVAILABLE 쿠폰을 돌려준다. (US-19)")
        @Test
        void issuesCoupon() {
            createUser("user1");
            Long templateId = saveTemplate("신규가입 1만원", DiscountType.FIXED, 10_000L, 30);

            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedResponse>> response = testRestTemplate.exchange(
                    "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(loginHeader("user1")), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().couponName()).isEqualTo("신규가입 1만원");
            assertThat(response.getBody().data().status()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("이미 발급받은 템플릿을 다시 발급하면 409 CONFLICT. (AC-19-2)")
        @Test
        void returnsConflict_whenAlreadyIssued() {
            createUser("user1");
            Long templateId = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedResponse>> type = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange("/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(loginHeader("user1")), type);

            ResponseEntity<ApiResponse<CouponV1Dto.IssuedResponse>> response = testRestTemplate.exchange(
                    "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(loginHeader("user1")), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 템플릿이면 404 NOT_FOUND. (AC-19-3)")
        @Test
        void returnsNotFound_whenTemplateMissing() {
            createUser("user1");
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedResponse>> response = testRestTemplate.exchange(
                    "/api/v1/coupons/99999/issue", HttpMethod.POST,
                    new HttpEntity<>(loginHeader("user1")), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("로그인 헤더가 없으면 400 BAD_REQUEST. (AC-19-4)")
        @Test
        void returnsBadRequest_whenNotLoggedIn() {
            Long templateId = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedResponse>> response = testRestTemplate.exchange(
                    "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("대고객 GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("본인 발급 쿠폰을 페이징하여 돌려준다. (US-20)")
        @Test
        void returnsMyCoupons() {
            createUser("user1");
            Long templateId = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedResponse>> issueType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange("/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(loginHeader("user1")), issueType);

            ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponPageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.MyCouponPageResponse>> response = testRestTemplate.exchange(
                    MY_COUPONS, HttpMethod.GET, new HttpEntity<>(loginHeader("user1")), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content().get(0).status()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("로그인 헤더가 없으면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenNotLoggedIn() {
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponPageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.MyCouponPageResponse>> response = testRestTemplate.exchange(
                    MY_COUPONS, HttpMethod.GET, new HttpEntity<>(empty()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("어드민 POST /api-admin/v1/coupons")
    @Nested
    class Register {

        @DisplayName("어드민이 템플릿을 등록하면 200 과 템플릿 정보를 돌려준다. (US-21)")
        @Test
        void registersTemplate() {
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.TemplateResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new CouponV1Dto.RegisterRequest("여름세일 10%", DiscountType.RATE, 10L, 0L, 7), adminHeader()),
                    type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().discountType()).isEqualTo(DiscountType.RATE);
            assertThat(response.getBody().data().discountValue()).isEqualTo(10L);
        }

        @DisplayName("어드민 헤더가 없으면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.TemplateResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.POST,
                    new HttpEntity<>(new CouponV1Dto.RegisterRequest("쿠폰", DiscountType.FIXED, 1_000L, 0L, 30), empty()),
                    type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("어드민 PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class Modify {

        @DisplayName("어드민이 수정하면 변경 내용이 반영된다. (US-22)")
        @Test
        void updatesTemplate() {
            Long templateId = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + templateId, HttpMethod.PUT,
                    new HttpEntity<>(new CouponV1Dto.ModifyRequest("변경", DiscountType.RATE, 20L, 0L, 14), adminHeader()),
                    type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            CouponTemplate reloaded = couponTemplateJpaRepository.findById(templateId).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("변경");
            assertThat(reloaded.getDiscountPolicy().getValue()).isEqualTo(20L);
        }

        @DisplayName("존재하지 않으면 404 NOT_FOUND. (AC-22-3)")
        @Test
        void returnsNotFound_whenMissing() {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/99999", HttpMethod.PUT,
                    new HttpEntity<>(new CouponV1Dto.ModifyRequest("변경", DiscountType.FIXED, 1_000L, 0L, 30), adminHeader()),
                    type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("어드민 DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class Delete {

        @DisplayName("어드민이 삭제하면 논리 삭제되고 목록에서 제외된다. (US-23)")
        @Test
        void softDeletesTemplate() {
            Long templateId = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN + "/" + templateId, HttpMethod.DELETE, new HttpEntity<>(adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(couponTemplateJpaRepository.findById(templateId).orElseThrow().getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("어드민 GET /api-admin/v1/coupons (목록) 과 /{couponId}/issues (발급내역)")
    @Nested
    class GetCouponsAndIssues {

        @DisplayName("템플릿 목록을 페이징하여 돌려준다. (AC-24-1)")
        @Test
        void returnsTemplatePage() {
            saveTemplate("쿠폰1", DiscountType.FIXED, 1_000L, 30);
            saveTemplate("쿠폰2", DiscountType.RATE, 10L, 30);

            ParameterizedTypeReference<ApiResponse<CouponV1Dto.TemplatePageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.TemplatePageResponse>> response = testRestTemplate.exchange(
                    ADMIN, HttpMethod.GET, new HttpEntity<>(adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(2);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2L);
        }

        @DisplayName("템플릿의 발급 내역(발급자·상태)을 돌려준다. (AC-24-2)")
        @Test
        void returnsIssueHistory() {
            createUser("user1");
            Long templateId = saveTemplate("쿠폰", DiscountType.FIXED, 1_000L, 30);
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedResponse>> issueType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange("/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(loginHeader("user1")), issueType);

            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueHistoryPageResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssueHistoryPageResponse>> response = testRestTemplate.exchange(
                    ADMIN + "/" + templateId + "/issues", HttpMethod.GET, new HttpEntity<>(adminHeader()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content().get(0).status()).isEqualTo(CouponStatus.AVAILABLE);
        }
    }
}
