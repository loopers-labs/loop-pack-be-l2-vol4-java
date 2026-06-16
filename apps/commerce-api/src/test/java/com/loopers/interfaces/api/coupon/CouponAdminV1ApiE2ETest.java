package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.CouponExpiry;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.apiadmin.coupon.CouponAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_ID = "admin1";
    private static final String ADMIN_PW = "Admin1234!";
    private static final String USER_LOGIN_ID = "user1";
    private static final String USER_PW = "User1234!";
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @BeforeEach
    void setUp() {
        userRepository.save(new UserModel(
                new UserId(ADMIN_ID),
                new Password(passwordEncoder.encode(ADMIN_PW)),
                new Name("관리자"),
                new BirthDay("1990-01-01"),
                new Email("admin@test.com"),
                UserRole.ADMIN
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", ADMIN_ID);
        headers.set("X-Loopers-LoginPw", ADMIN_PW);
        return headers;
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", USER_LOGIN_ID);
        headers.set("X-Loopers-LoginPw", USER_PW);
        return headers;
    }

    private UserModel saveDefaultUser() {
        return userRepository.save(new UserModel(
                new UserId(USER_LOGIN_ID),
                new Password(passwordEncoder.encode(USER_PW)),
                new Name("일반유저"),
                new BirthDay("1990-01-01"),
                new Email("user@test.com"),
                UserRole.USER
        ));
    }

    private CouponModel saveValidCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "10% 할인 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(FUTURE)
        ));
    }

    private CouponModel saveExpiredCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "만료 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(PAST)
        ));
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class Register {

        @DisplayName("인증 헤더 없이 요청하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            CouponAdminV1Dto.RegisterRequest request = new CouponAdminV1Dto.RegisterRequest(
                    "10% 할인 쿠폰", CouponType.RATE, 10L, null, FUTURE);
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons", HttpMethod.POST,
                            new HttpEntity<>(request, new HttpHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("USER 권한으로 요청하면, 403 FORBIDDEN을 반환한다.")
        @Test
        void returnsForbidden_whenUserRoleIsNotAdmin() {
            saveDefaultUser();
            CouponAdminV1Dto.RegisterRequest request = new CouponAdminV1Dto.RegisterRequest(
                    "10% 할인 쿠폰", CouponType.RATE, 10L, null, FUTURE);
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons", HttpMethod.POST,
                            new HttpEntity<>(request, userHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("유효한 정보이면, 201 CREATED와 쿠폰 정보를 반환한다.")
        @Test
        void returnsCoupon_whenRequestIsValid() {
            CouponAdminV1Dto.RegisterRequest request = new CouponAdminV1Dto.RegisterRequest(
                    "10% 할인 쿠폰", CouponType.RATE, 10L, null, FUTURE);
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons", HttpMethod.POST,
                            new HttpEntity<>(request, adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().name()).isEqualTo("10% 할인 쿠폰");
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {

        @DisplayName("쿠폰이 존재하면, 200 OK와 쿠폰 정보를 반환한다.")
        @Test
        void returnsCoupon_whenCouponExists() {
            CouponModel coupon = saveValidCoupon();
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId(), HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(coupon.getId());
        }

        @DisplayName("쿠폰이 존재하지 않으면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/999", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetList {

        @DisplayName("쿠폰 목록이 있으면, 200 OK와 페이지를 반환한다.")
        @Test
        void returnsPage_whenCouponsExist() {
            saveValidCoupon();
            saveValidCoupon();
            ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons?page=0&size=20", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
        }
    }

    @DisplayName("PATCH /api-admin/v1/coupons/{couponId}")
    @Nested
    class Update {

        @DisplayName("이름을 수정하면, 200 OK와 변경된 쿠폰 정보를 반환한다.")
        @Test
        void returnsCoupon_whenNameIsUpdated() {
            CouponModel coupon = saveValidCoupon();
            CouponAdminV1Dto.UpdateRequest request = new CouponAdminV1Dto.UpdateRequest("변경된 쿠폰 이름", null);
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId(), HttpMethod.PATCH,
                            new HttpEntity<>(request, adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("변경된 쿠폰 이름");
        }

        @DisplayName("만료일을 연장하면, 200 OK와 변경된 쿠폰 정보를 반환한다.")
        @Test
        void returnsCoupon_whenExpiredAtIsExtended() {
            CouponModel coupon = saveValidCoupon();
            ZonedDateTime newExpiredAt = FUTURE.plusDays(10);
            CouponAdminV1Dto.UpdateRequest request = new CouponAdminV1Dto.UpdateRequest(null, newExpiredAt);
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId(), HttpMethod.PATCH,
                            new HttpEntity<>(request, adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().expiredAt()).isAfter(FUTURE);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class Delete {

        @DisplayName("만료된 쿠폰이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenCouponIsExpired() {
            CouponModel coupon = saveExpiredCoupon();
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId(), HttpMethod.DELETE,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("만료되지 않은 쿠폰이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenCouponIsNotExpired() {
            CouponModel coupon = saveValidCoupon();
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId(), HttpMethod.DELETE,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssues {

        @DisplayName("발급 내역이 있으면, 200 OK와 발급 내역을 반환한다.")
        @Test
        void returnsIssues_whenIssuesExist() {
            UserModel user = saveDefaultUser();
            CouponModel coupon = saveValidCoupon();
            userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon));
            ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId() + "/issues?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
        }

        @DisplayName("발급 내역이 없으면, 200 OK와 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuesExist() {
            CouponModel coupon = saveValidCoupon();
            ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>>> response =
                    testRestTemplate.exchange("/api-admin/v1/coupons/" + coupon.getId() + "/issues?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isZero();
        }
    }
}
