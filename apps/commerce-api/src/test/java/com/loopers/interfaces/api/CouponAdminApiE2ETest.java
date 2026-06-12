package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponDto;
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
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminApiE2ETest {

    private static final String TEMPLATES_URL = "/api-admin/v1/coupons";
    private static final String TEMPLATE_URL = "/api-admin/v1/coupons/{id}";
    private static final String ISSUANCES_URL = "/api-admin/v1/coupons/{id}/issues";

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_HEADER_VALUE = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;
    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        savedUser = userService.signUp(new UserModel(
            "user01", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "user@example.com"
        ));
        adminHeaders = new HttpHeaders();
        adminHeaders.set(ADMIN_HEADER, ADMIN_HEADER_VALUE);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/coupon-templates")
    @Nested
    class CreateTemplate {

        @DisplayName("A-1: ADMIN이 유효한 FIXED 템플릿을 생성하면 201과 templateId가 반환된다.")
        @Test
        void returns201_whenValidFixedTemplate() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("1000원 할인", CouponType.FIXED, 1000L, 5000L, LocalDateTime.now().plusDays(7));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<CouponDto.TemplateResponse>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED);
        }

        @DisplayName("A-2: ADMIN이 유효한 RATE 템플릿을 생성하면 201과 templateId가 반환된다.")
        @Test
        void returns201_whenValidRateTemplate() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<CouponDto.TemplateResponse>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().id()).isNotNull();
            assertThat(response.getBody().data().type()).isEqualTo(CouponType.RATE);
        }

        @DisplayName("A-3: 비관리자가 템플릿을 생성하면 401이 반환된다.")
        @Test
        void returns401_whenNotAdmin() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7));
            var noAuthHeaders = new HttpHeaders();
            noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, noAuthHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("A-4: name이 빈 값이면 400이 반환된다.")
        @Test
        void returns400_whenNameIsBlank() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("A-5: value가 0 이하이면 400이 반환된다.")
        @Test
        void returns400_whenValueIsZeroOrNegative() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("1000원 할인", CouponType.FIXED, 0L, null, LocalDateTime.now().plusDays(7));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("A-6: RATE 타입에서 value가 101 이상이면 400이 반환된다.")
        @Test
        void returns400_whenRateValueExceedsHundred() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("101% 할인", CouponType.RATE, 101L, null, LocalDateTime.now().plusDays(7));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("A-7: expiredAt이 과거이면 400이 반환된다.")
        @Test
        void returns400_whenExpiredAtIsInPast() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().minusDays(1));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("A-8: minOrderAmount가 음수이면 400이 반환된다.")
        @Test
        void returns400_whenMinOrderAmountIsNegative() {
            // arrange
            var request = new CouponDto.TemplateCreateRequest("1000원 할인", CouponType.FIXED, 1000L, -1L, LocalDateTime.now().plusDays(7));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/coupon-templates")
    @Nested
    class GetTemplates {

        @DisplayName("A-9: ADMIN이 템플릿 목록을 조회하면 200과 페이지 결과가 반환된다.")
        @Test
        void returns200WithPage_whenAdminRequests() {
            // arrange
            couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));
            couponTemplateJpaRepository.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATES_URL + "?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<ApiResponse<CouponDto.TemplatePageResponse>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isGreaterThanOrEqualTo(2);
            assertThat(response.getBody().data().templates()).isNotEmpty();
        }
    }

    @DisplayName("GET /api-admin/v1/coupon-templates/{id}")
    @Nested
    class GetTemplate {

        @DisplayName("A-10: ADMIN이 존재하는 템플릿을 조회하면 200과 상세 정보가 반환된다.")
        @Test
        void returns200WithDetails_whenTemplateExists() {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATE_URL, HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<ApiResponse<CouponDto.TemplateResponse>>() {},
                template.getId()
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(template.getId());
            assertThat(response.getBody().data().name()).isEqualTo("1000원 할인");
        }

        @DisplayName("A-11: 존재하지 않는 템플릿을 조회하면 404가 반환된다.")
        @Test
        void returns404_whenTemplateNotFound() {
            // act
            var response = testRestTemplate.exchange(
                TEMPLATE_URL, HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {},
                999L
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api-admin/v1/coupon-templates/{id}")
    @Nested
    class UpdateTemplate {

        @DisplayName("A-12: ADMIN이 name과 isActive를 수정하면 200과 수정된 값이 반환된다.")
        @Test
        void returns200WithUpdatedValues_whenTemplateExists() {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));
            var request = new CouponDto.TemplateUpdateRequest("1000원 할인 (수정)", false);

            // act
            var response = testRestTemplate.exchange(
                TEMPLATE_URL, HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<CouponDto.TemplateResponse>>() {},
                template.getId()
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("1000원 할인 (수정)");
            assertThat(response.getBody().data().isActive()).isFalse();
        }

        @DisplayName("A-13: 존재하지 않는 템플릿을 수정하면 404가 반환된다.")
        @Test
        void returns404_whenTemplateNotFound() {
            // arrange
            var request = new CouponDto.TemplateUpdateRequest("수정된 이름", true);

            // act
            var response = testRestTemplate.exchange(
                TEMPLATE_URL, HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {},
                999L
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/coupon-templates/{id}/issuances")
    @Nested
    class GetIssuances {

        @DisplayName("A-14: ADMIN이 발급 현황을 조회하면 200과 UserCoupon 목록이 반환된다.")
        @Test
        void returns200WithIssuanceList_whenAdminRequests() {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));
            userCouponJpaRepository.save(new UserCouponModel(savedUser.getId(), template.getId()));

            // act
            var response = testRestTemplate.exchange(
                ISSUANCES_URL + "?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<ApiResponse<CouponDto.IssuancePageResponse>>() {},
                template.getId()
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
            assertThat(response.getBody().data().issuances()).hasSize(1);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteTemplate {

        @DisplayName("A-15: ADMIN이 템플릿을 삭제하면 204가 반환되고 isBlocked가 true가 된다.")
        @Test
        void returns204AndBlocked_whenTemplateExists() {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));

            // act
            var response = testRestTemplate.exchange(
                TEMPLATE_URL, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {},
                template.getId()
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            var deleted = couponTemplateJpaRepository.findById(template.getId()).orElseThrow();
            assertThat(deleted.isBlocked()).isTrue();
        }

        @DisplayName("A-16: 존재하지 않는 템플릿을 삭제하면 404가 반환된다.")
        @Test
        void returns404_whenTemplateNotFound() {
            // act
            var response = testRestTemplate.exchange(
                TEMPLATE_URL, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {},
                999L
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
