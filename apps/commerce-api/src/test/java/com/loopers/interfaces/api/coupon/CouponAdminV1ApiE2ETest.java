package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/coupons";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplateModel saveCouponTemplate(String name, CouponType type, BigDecimal value) {
        return couponTemplateRepository.save(new CouponTemplateModel(
                name,
                type,
                value,
                BigDecimal.valueOf(10000),
                ZonedDateTime.now().plusDays(30)
        ));
    }

    private HttpEntity<Void> adminHeaderEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LDAP, ADMIN_LDAP);
        return new HttpEntity<>(null, headers);
    }

    private <T> HttpEntity<T> adminJsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AuthHeaders.LDAP, ADMIN_LDAP);
        return new HttpEntity<>(body, headers);
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetCoupons {

        @DisplayName("등록된 쿠폰 템플릿 목록을 페이지네이션으로 조회한다.")
        @Test
        void couponTemplatesArePagedOnRequest() {
            // given
            CouponTemplateModel templateA = saveCouponTemplate("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10));
            CouponTemplateModel templateB = saveCouponTemplate("여름 시즌 5000원 할인", CouponType.FIXED, BigDecimal.valueOf(5000));

            // when
            String url = BASE_URL + "?page=0&size=20";
            ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .anyMatch(c -> c.id().equals(templateA.getId()) && c.name().equals("신규가입 10% 할인")),
                    () -> assertThat(response.getBody().data().content())
                            .anyMatch(c -> c.id().equals(templateB.getId()) && c.name().equals("여름 시즌 5000원 할인"))
            );
        }

        @DisplayName("LDAP 헤더가 없으면 쿠폰 템플릿 목록을 조회할 수 없다.")
        @Test
        void couponTemplateListIsNotAccessible_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL, HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 LDAP로 요청하면 쿠폰 템플릿 목록을 조회할 수 없다.")
        @Test
        void couponTemplateListIsNotAccessible_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL, HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {

        @DisplayName("등록된 쿠폰 템플릿의 상세 정보를 조회한다.")
        @Test
        void couponTemplateDetailIsReturned_whenTemplateExists() {
            // given
            CouponTemplateModel template = saveCouponTemplate("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10));

            // when
            String url = BASE_URL + "/" + template.getId();
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(template.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponAdminV1Dto.CouponTypeDto.RATE),
                    () -> assertThat(response.getBody().data().value()).isEqualByComparingTo(BigDecimal.valueOf(10)),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000))
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿은 조회할 수 없다.")
        @Test
        void couponTemplateDetailCannotBeFound_whenTemplateDoesNotExist() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/99999", HttpMethod.GET, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LDAP 헤더가 없으면 쿠폰 템플릿 상세를 조회할 수 없다.")
        @Test
        void couponTemplateDetailIsNotAccessible_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1", HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 LDAP로 요청하면 쿠폰 템플릿 상세를 조회할 수 없다.")
        @Test
        void couponTemplateDetailIsNotAccessible_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1", HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 요청으로 쿠폰 템플릿을 등록하면 생성된 템플릿이 반환되고 DB에 저장된다.")
        @Test
        void couponTemplateIsRegistered_whenValidRequestIsProvided() {
            // given
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);
            CouponAdminV1Dto.CouponCreateRequest request = new CouponAdminV1Dto.CouponCreateRequest(
                    "신규가입 10% 할인",
                    CouponAdminV1Dto.CouponTypeDto.RATE,
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000),
                    expiredAt
            );

            // when
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, adminJsonEntity(request), responseType);

            // then
            CouponTemplateModel persisted = couponTemplateRepository.findById(response.getBody().data().id()).orElseThrow();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponAdminV1Dto.CouponTypeDto.RATE),
                    () -> assertThat(response.getBody().data().value()).isEqualByComparingTo(BigDecimal.valueOf(10)),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(persisted.getName()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(persisted.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(10))
            );
        }

        @DisplayName("LDAP 헤더 없이 요청하면 쿠폰 템플릿을 등록할 수 없다.")
        @Test
        void couponTemplateRegistrationIsNotAllowed_whenLdapHeaderIsMissing() {
            // given
            CouponAdminV1Dto.CouponCreateRequest request = new CouponAdminV1Dto.CouponCreateRequest(
                    "신규가입 10% 할인",
                    CouponAdminV1Dto.CouponTypeDto.RATE,
                    BigDecimal.valueOf(10),
                    null,
                    ZonedDateTime.now().plusDays(30)
            );

            // when
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL, HttpMethod.POST, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 LDAP로 요청하면 쿠폰 템플릿을 등록할 수 없다.")
        @Test
        void couponTemplateRegistrationIsNotAllowed_whenLdapIsInvalid() {
            // given
            CouponAdminV1Dto.CouponCreateRequest request = new CouponAdminV1Dto.CouponCreateRequest(
                    "신규가입 10% 할인",
                    CouponAdminV1Dto.CouponTypeDto.RATE,
                    BigDecimal.valueOf(10),
                    null,
                    ZonedDateTime.now().plusDays(30)
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL, HttpMethod.POST, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        @DisplayName("유효한 요청으로 쿠폰 템플릿을 수정하면 수정된 템플릿이 반환되고 DB에 반영된다.")
        @Test
        void couponTemplateIsUpdated_whenValidRequestIsProvided() {
            // given
            CouponTemplateModel template = saveCouponTemplate("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10));
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);
            CouponAdminV1Dto.CouponUpdateRequest request = new CouponAdminV1Dto.CouponUpdateRequest(
                    "여름 시즌 5000원 할인",
                    CouponAdminV1Dto.CouponTypeDto.FIXED,
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(20000),
                    newExpiredAt
            );

            // when
            String url = BASE_URL + "/" + template.getId();
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.PUT, adminJsonEntity(request), responseType);

            // then
            CouponTemplateModel persisted = couponTemplateRepository.findById(template.getId()).orElseThrow();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(template.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("여름 시즌 5000원 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponAdminV1Dto.CouponTypeDto.FIXED),
                    () -> assertThat(response.getBody().data().value()).isEqualByComparingTo(BigDecimal.valueOf(5000)),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(persisted.getName()).isEqualTo("여름 시즌 5000원 할인"),
                    () -> assertThat(persisted.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(5000))
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿은 수정할 수 없다.")
        @Test
        void couponTemplateCannotBeUpdated_whenTemplateDoesNotExist() {
            // given
            CouponAdminV1Dto.CouponUpdateRequest request = new CouponAdminV1Dto.CouponUpdateRequest(
                    "여름 시즌 5000원 할인",
                    CouponAdminV1Dto.CouponTypeDto.FIXED,
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(20000),
                    ZonedDateTime.now().plusDays(60)
            );

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/99999", HttpMethod.PUT, adminJsonEntity(request), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LDAP 헤더 없이 요청하면 쿠폰 템플릿을 수정할 수 없다.")
        @Test
        void couponTemplateUpdateIsNotAllowed_whenLdapHeaderIsMissing() {
            // given
            CouponAdminV1Dto.CouponUpdateRequest request = new CouponAdminV1Dto.CouponUpdateRequest(
                    "여름 시즌 5000원 할인",
                    CouponAdminV1Dto.CouponTypeDto.FIXED,
                    BigDecimal.valueOf(5000),
                    null,
                    ZonedDateTime.now().plusDays(60)
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1", HttpMethod.PUT, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 LDAP로 요청하면 쿠폰 템플릿을 수정할 수 없다.")
        @Test
        void couponTemplateUpdateIsNotAllowed_whenLdapIsInvalid() {
            // given
            CouponAdminV1Dto.CouponUpdateRequest request = new CouponAdminV1Dto.CouponUpdateRequest(
                    "여름 시즌 5000원 할인",
                    CouponAdminV1Dto.CouponTypeDto.FIXED,
                    BigDecimal.valueOf(5000),
                    null,
                    ZonedDateTime.now().plusDays(60)
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1", HttpMethod.PUT, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteCoupon {

        @DisplayName("쿠폰 템플릿을 삭제하면 성공 응답이 반환되고 DB에서 조회되지 않는다.")
        @Test
        void couponTemplateIsDeleted_whenValidRequestIsProvided() {
            // given
            CouponTemplateModel template = saveCouponTemplate("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10));

            // when
            String url = BASE_URL + "/" + template.getId();
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    url, HttpMethod.DELETE, adminHeaderEntity(),
                    new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(couponTemplateRepository.findById(template.getId())).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿은 삭제할 수 없다.")
        @Test
        void couponTemplateCannotBeDeleted_whenTemplateDoesNotExist() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/99999", HttpMethod.DELETE, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LDAP 헤더가 없으면 쿠폰 템플릿을 삭제할 수 없다.")
        @Test
        void couponTemplateDeletionIsNotAllowed_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1", HttpMethod.DELETE, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 LDAP로 요청하면 쿠폰 템플릿을 삭제할 수 없다.")
        @Test
        void couponTemplateDeletionIsNotAllowed_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1", HttpMethod.DELETE, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("발급된 쿠폰이 있으면 페이지네이션된 발급 내역을 반환한다.")
        @Test
        void issuedCouponsArePagedOnRequest_whenIssuedCouponsExist() {
            // given
            CouponTemplateModel template = saveCouponTemplate("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 1L));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 2L));

            // when
            String url = BASE_URL + "/" + template.getId() + "/issues?page=0&size=20";
            ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .allMatch(c -> c.couponTemplateId().equals(template.getId()))
            );
        }

        @DisplayName("발급된 쿠폰이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuedCouponsExist() {
            // given
            CouponTemplateModel template = saveCouponTemplate("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10));

            // when
            String url = BASE_URL + "/" + template.getId() + "/issues?page=0&size=20";
            ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().content()).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿의 발급 내역을 조회하면 404가 반환된다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/99999/issues", HttpMethod.GET, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LDAP 헤더가 없으면 발급 내역을 조회할 수 없다.")
        @Test
        void issuedCouponListIsNotAccessible_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1/issues", HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 LDAP로 요청하면 발급 내역을 조회할 수 없다.")
        @Test
        void issuedCouponListIsNotAccessible_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/1/issues", HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
