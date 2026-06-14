package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String ENDPOINT_COUPONS = "/api-admin/v1/coupons";
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    private final TestRestTemplate testRestTemplate;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "admin01");
        return headers;
    }

    private CouponAdminV1Dto.CreateCouponRequest createRequest() {
        return new CouponAdminV1Dto.CreateCouponRequest(
            "신규 10% 할인", CouponType.RATE, 10L, BigDecimal.valueOf(10000), EXPIRED_AT
        );
    }

    private Long registerAndGetId() {
        ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
            ENDPOINT_COUPONS, HttpMethod.POST,
            new HttpEntity<>(createRequest(), adminHeaders()),
            new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class Register {
        @DisplayName("LDAP 헤더와 함께 등록하면, 템플릿이 생성된다.")
        @Test
        void registersTemplate_whenLdapHeaderPresent() {
            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT_COUPONS, HttpMethod.POST,
                new HttpEntity<>(createRequest(), adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규 10% 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.RATE),
                () -> assertThat(couponJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("LDAP 헤더가 없으면, 401 UNAUTHORIZED 응답을 받고 템플릿이 저장되지 않는다.")
        @Test
        void throwsUnauthorized_whenLdapHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT_COUPONS, HttpMethod.POST,
                new HttpEntity<>(createRequest()), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetCoupons {
        @DisplayName("등록된 템플릿 목록을 페이지로 반환한다.")
        @Test
        void returnsCouponPage() {
            // arrange
            registerAndGetId();

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponPageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT_COUPONS + "?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {
        @DisplayName("템플릿 상세를 반환한다.")
        @Test
        void returnsCouponDetail() {
            // arrange
            Long couponId = registerAndGetId();

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT_COUPONS + "/" + couponId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(couponId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규 10% 할인")
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class Update {
        @DisplayName("템플릿을 수정하면, 변경된 값이 반영된다.")
        @Test
        void updatesTemplate() {
            // arrange
            Long couponId = registerAndGetId();
            CouponAdminV1Dto.UpdateCouponRequest request = new CouponAdminV1Dto.UpdateCouponRequest(
                "변경된 5000원 할인", CouponType.FIXED, 5000L, BigDecimal.valueOf(30000), EXPIRED_AT
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                ENDPOINT_COUPONS + "/" + couponId, HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("변경된 5000원 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(response.getBody().data().value()).isEqualTo(5000L)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class Delete {
        @DisplayName("템플릿을 삭제하면, 목록에서 제외된다.")
        @Test
        void removesTemplateFromList() {
            // arrange
            Long couponId = registerAndGetId();

            // act
            ResponseEntity<ApiResponse<Object>> deleteResponse = testRestTemplate.exchange(
                ENDPOINT_COUPONS + "/" + couponId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            // assert
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponPageResponse>> listResponse = testRestTemplate.exchange(
                ENDPOINT_COUPONS + "?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});
            assertAll(
                () -> assertTrue(deleteResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(listResponse.getBody().data().items()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssues {
        @DisplayName("특정 템플릿의 발급 내역을 페이지로 반환한다.")
        @Test
        void returnsIssuePage() {
            // arrange
            Long couponId = registerAndGetId();
            userCouponJpaRepository.save(new UserCoupon(1L, couponId));
            userCouponJpaRepository.save(new UserCoupon(2L, couponId));

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.IssuePageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.IssuePageResponse>> response = testRestTemplate.exchange(
                ENDPOINT_COUPONS + "/" + couponId + "/issues?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }
    }
}
