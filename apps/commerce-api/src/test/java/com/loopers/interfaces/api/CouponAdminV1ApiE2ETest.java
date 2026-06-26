package com.loopers.interfaces.api;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.admin.AdminAuthInterceptor;
import com.loopers.interfaces.api.admin.coupon.CouponAdminV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
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
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @Autowired
    public CouponAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        User user = userJpaRepository.save(
            new User("tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        this.userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AdminAuthInterceptor.HEADER_LDAP, "admin.user");
        return headers;
    }

    private Coupon persistCoupon() {
        return couponJpaRepository.save(
            new Coupon("10% 할인", CouponType.RATE, 10L, 10000L, ZonedDateTime.now().plusDays(30)));
    }

    @DisplayName("관리자 인증")
    @Nested
    class AdminAuth {

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 Unauthorized 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("쿠폰 템플릿 CRUD")
    @Nested
    class Crud {

        @DisplayName("등록하면, 2xx 응답과 등록된 템플릿을 반환한다.")
        @Test
        void createsCoupon() {
            // arrange
            CouponAdminV1Dto.CouponRequest request = new CouponAdminV1Dto.CouponRequest(
                "신규가입 10% 할인", "RATE", 10L, 10000L, ZonedDateTime.now().plusDays(30));

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo("RATE"),
                () -> assertThat(response.getBody().data().value()).isEqualTo(10L),
                () -> assertThat(response.getBody().data().minOrderAmount()).isEqualTo(10000L)
            );
        }

        @DisplayName("잘못된 타입으로 등록하면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenInvalidType() {
            // arrange
            CouponAdminV1Dto.CouponRequest request = new CouponAdminV1Dto.CouponRequest(
                "이상한 쿠폰", "PERCENT", 10L, null, ZonedDateTime.now().plusDays(30));

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("목록과 상세를 조회하면, 등록된 템플릿이 반환된다.")
        @Test
        void getsCoupons() {
            // arrange
            Coupon coupon = persistCoupon();

            // act
            ParameterizedTypeReference<ApiResponse<List<CouponAdminV1Dto.CouponResponse>>> listType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponAdminV1Dto.CouponResponse>>> listResponse = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(adminHeaders()), listType
            );
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> detailType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> detailResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.getId(), HttpMethod.GET, new HttpEntity<>(adminHeaders()), detailType
            );

            // assert
            assertAll(
                () -> assertThat(listResponse.getBody().data()).hasSize(1),
                () -> assertThat(detailResponse.getBody().data().id()).isEqualTo(coupon.getId()),
                () -> assertThat(detailResponse.getBody().data().name()).isEqualTo("10% 할인")
            );
        }

        @DisplayName("수정하면, 변경된 정책이 반영된다.")
        @Test
        void updatesCoupon() {
            // arrange
            Coupon coupon = persistCoupon();
            CouponAdminV1Dto.CouponRequest request = new CouponAdminV1Dto.CouponRequest(
                "5천원 할인", "FIXED", 5000L, null, ZonedDateTime.now().plusDays(60));

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.getId(), HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("5천원 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                () -> assertThat(response.getBody().data().value()).isEqualTo(5000L)
            );
        }

        @DisplayName("삭제하면 상세 조회는 404 가 되지만, 기발급 쿠폰은 계속 사용 가능 상태로 조회된다. (스냅샷 계약)")
        @Test
        void deleteStopsIssuance_butIssuedCouponsSurvive() {
            // arrange
            Coupon coupon = persistCoupon();
            userCouponJpaRepository.save(coupon.issueTo(userId, ZonedDateTime.now())); // 삭제 전 발급

            // act — 삭제
            ParameterizedTypeReference<ApiResponse<Object>> deleteType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> deleteResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.getId(), HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), deleteType
            );

            // act — 삭제 후 관리자 상세 조회
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> detailType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> detailResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.getId(), HttpMethod.GET, new HttpEntity<>(adminHeaders()), detailType
            );

            // act — 기발급 쿠폰은 대고객 목록에서 여전히 정상
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.set(AuthHeaders.HEADER_LOGIN_ID, "tester01");
            userHeaders.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
            ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> myCouponsType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> myCouponsResponse =
                testRestTemplate.exchange(
                    "/api/v1/users/me/coupons", HttpMethod.GET, new HttpEntity<>(userHeaders), myCouponsType
                );

            // assert
            assertAll(
                () -> assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(myCouponsResponse.getBody().data()).hasSize(1),
                () -> assertThat(myCouponsResponse.getBody().data().get(0).name()).isEqualTo("10% 할인"),
                () -> assertThat(myCouponsResponse.getBody().data().get(0).status()).isEqualTo("AVAILABLE")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class IssuedCoupons {

        @DisplayName("발급 내역을 조회하면, 발급된 쿠폰 목록이 반환된다.")
        @Test
        void getsIssuedCoupons() {
            // arrange
            Coupon coupon = persistCoupon();
            ZonedDateTime now = ZonedDateTime.now();
            userCouponJpaRepository.save(coupon.issueTo(userId, now));
            userCouponJpaRepository.save(coupon.issueTo(userId, now));

            // act
            ParameterizedTypeReference<ApiResponse<List<CouponAdminV1Dto.IssuedCouponResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<CouponAdminV1Dto.IssuedCouponResponse>>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + coupon.getId() + "/issues?page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data()).hasSize(2),
                () -> assertThat(response.getBody().data().get(0).userId()).isEqualTo(userId),
                () -> assertThat(response.getBody().data().get(0).status()).isEqualTo("AVAILABLE")
            );
        }
    }
}
