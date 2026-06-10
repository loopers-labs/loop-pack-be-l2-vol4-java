package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api-admin/v1/coupons";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> adminJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> jsonRequestWithoutAdmin(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> adminGet() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(new HttpHeaders());
    }

    private CouponModel saveCoupon(String name, Integer minOrderAmount) {
        CouponModel coupon = CouponModel.builder()
            .rawName(name)
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build();

        return couponJpaRepository.save(coupon);
    }

    private CouponModel saveExpiredCoupon(String name) {
        ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);
        CouponModel coupon = CouponModel.builder()
            .rawName(name)
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(pastExpiredAt)
            .now(pastExpiredAt.minusDays(1))
            .build();

        return couponJpaRepository.save(coupon);
    }

    @DisplayName("쿠폰 템플릿 등록 - POST /api-admin/v1/coupons")
    @Nested
    class CreateCoupon {

        private final ZonedDateTime futureExpiredAt = ZonedDateTime.now().plusDays(7);

        @DisplayName("정상 요청이면, 201 Created와 함께 couponId가 응답 본문에 담겨 반환된다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("신규 가입 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("couponId"),
                () -> assertThat(response.getBody().data().get("couponId")).isNotNull(),
                () -> assertThat(couponJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("최소 주문 금액을 생략한 정상 요청이면, 201 Created로 쿠폰 템플릿이 생성된다.")
        @Test
        void returnsCreated_whenMinOrderAmountIsOmitted() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("조건 없는 쿠폰", DiscountType.FIXED, 3_000, null, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("couponId")).isNotNull(),
                () -> assertThat(couponJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("신규 가입 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("할인 타입이 허용되지 않는 값이면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenDiscountTypeIsNotAllowed() {
            // arrange
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "잘못된 타입 쿠폰");
            requestBody.put("discountType", "PERCENT");
            requestBody.put("discountValue", 10);
            requestBody.put("expiredAt", futureExpiredAt.toString());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("쿠폰 이름이 100자를 초과하면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("쿠".repeat(101), DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenRateValueExceedsMax() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("정률 쿠폰", DiscountType.RATE, 101, null, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("만료 시각이 현재 시각 이전이면, 400 Bad Request로 거절되고 쿠폰 템플릿은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenExpiredAtIsPast() {
            // arrange
            CouponAdminV1Dto.CreateRequest requestBody =
                new CouponAdminV1Dto.CreateRequest("만료된 쿠폰", DiscountType.FIXED, 5_000, null, ZonedDateTime.now().minusDays(1));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(couponJpaRepository.findAll()).isEmpty()
            );
        }
    }

    @DisplayName("쿠폰 템플릿 수정 - PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        private final ZonedDateTime futureExpiredAt = ZonedDateTime.now().plusDays(7);

        @DisplayName("정상 요청이면, 200 OK와 함께 couponId가 반환되고 이름이 갱신된다.")
        @Test
        void returnsOk_andUpdatesName_whenRequestIsValid() {
            // arrange
            CouponModel savedCoupon = saveCoupon("기존 쿠폰", 10_000);
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("변경 쿠폰", DiscountType.RATE, 20, 50_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            CouponModel reloadedCoupon = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("couponId")).isNotNull(),
                () -> assertThat(reloadedCoupon.getName().value()).isEqualTo("변경 쿠폰")
            );
        }

        @DisplayName("기존과 동일한 값으로 수정해도, 200 OK로 정상 처리된다.")
        @Test
        void returnsOk_whenUpdatedWithSameValues() {
            // arrange
            CouponModel savedCoupon = saveCoupon("기존 쿠폰", 10_000);
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("기존 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("최소 주문 금액을 생략해 수정하면, 200 OK로 제약 없음(0)으로 비워진다.")
        @Test
        void returnsOk_andClearsMinOrderAmount_whenOmitted() {
            // arrange
            CouponModel savedCoupon = saveCoupon("기존 쿠폰", 10_000);
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("기존 쿠폰", DiscountType.FIXED, 5_000, null, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            CouponModel reloadedCoupon = couponJpaRepository.findById(savedCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(reloadedCoupon.getMinOrderAmount().value()).isZero()
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponModel savedCoupon = saveCoupon("기존 쿠폰", 10_000);
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("변경 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.PUT,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("대상 템플릿이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTargetIsAbsent() {
            // arrange
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("변경 쿠폰", DiscountType.FIXED, 5_000, 10_000, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999",
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenRateValueExceedsMax() {
            // arrange
            CouponModel savedCoupon = saveCoupon("기존 쿠폰", 10_000);
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("정률 쿠폰", DiscountType.RATE, 101, null, futureExpiredAt);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("만료 시각이 현재 시각 이전이면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenExpiredAtIsPast() {
            // arrange
            CouponModel savedCoupon = saveCoupon("기존 쿠폰", 10_000);
            CouponAdminV1Dto.UpdateRequest requestBody =
                new CouponAdminV1Dto.UpdateRequest("기존 쿠폰", DiscountType.FIXED, 5_000, 10_000, ZonedDateTime.now().minusDays(1));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }
    }

    @DisplayName("쿠폰 템플릿 삭제 - DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteCoupon {

        @DisplayName("정상 요청이면, 200 OK로 처리되고 템플릿이 활성 조회에서 제외된다.")
        @Test
        void returnsOk_andSoftDeletes_whenRequestIsValid() {
            // arrange
            CouponModel savedCoupon = saveCoupon("삭제 대상 쿠폰", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.DELETE,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(couponJpaRepository.findById(savedCoupon.getId()).orElseThrow().getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("동일한 템플릿에 삭제를 두 번 요청해도, 두 응답 모두 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenDeletedTwice() {
            // arrange
            CouponModel savedCoupon = saveCoupon("삭제 대상 쿠폰", 10_000);
            String endpoint = ENDPOINT_REGISTER + "/" + savedCoupon.getId();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> firstResponse = testRestTemplate.exchange(
                endpoint, HttpMethod.DELETE, adminGet(), MAP_RESPONSE);
            ResponseEntity<ApiResponse<Map<String, Object>>> secondResponse = testRestTemplate.exchange(
                endpoint, HttpMethod.DELETE, adminGet(), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("대상 템플릿이 존재하지 않아도, 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenTargetIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999",
                HttpMethod.DELETE,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponModel savedCoupon = saveCoupon("삭제 대상 쿠폰", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.DELETE,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }

    @DisplayName("쿠폰 템플릿 목록 - GET /api-admin/v1/coupons")
    @Nested
    class ReadCoupons {

        @DisplayName("정상 요청이면, 200 OK와 함께 삭제되지 않은 템플릿이 등록 시각 내림차순으로 반환된다.")
        @Test
        void returnsOk_withActiveCouponsAndMeta() {
            // arrange
            saveCoupon("쿠폰1", null);
            saveCoupon("쿠폰2", 10_000);
            CouponModel deletedCoupon = saveCoupon("쿠폰3", 10_000);
            deletedCoupon.delete();
            couponJpaRepository.saveAndFlush(deletedCoupon);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content =
                (List<Map<String, Object>>) response.getBody().data().get("content");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsKeys("content", "page", "size", "totalElements", "totalPages"),
                () -> assertThat(content).hasSize(2),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(2L),
                () -> assertThat(content)
                    .extracting(item -> item.get("name"))
                    .containsExactlyInAnyOrder("쿠폰1", "쿠폰2"),
                () -> assertThat(content)
                    .extracting(item -> (String) item.get("createdAt"))
                    .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(content.get(0))
                    .containsOnlyKeys("couponId", "name", "discountType", "discountValue", "minOrderAmount", "expiredAt", "createdAt", "updatedAt")
            );
        }

        @DisplayName("size를 지정하지 않으면, 기본 20건으로 페이징된다.")
        @Test
        void returnsOk_withDefaultSize_whenSizeIsOmitted() {
            // arrange
            saveCoupon("쿠폰1", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(((Number) response.getBody().data().get("size")).intValue()).isEqualTo(20)
            );
        }

        @DisplayName("활성 템플릿이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent_whenNoCoupons() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((java.util.List<?>) response.getBody().data().get("content")).isEmpty(),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(0L)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }

    @DisplayName("쿠폰 템플릿 상세 - GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class ReadCouponDetail {

        @DisplayName("정상 요청이면, 200 OK와 함께 상세 정보가 반환된다.")
        @Test
        void returnsOk_withDetailFields() {
            // arrange
            CouponModel savedCoupon = saveCoupon("신규 쿠폰", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsOnlyKeys("couponId", "name", "discountType", "discountValue", "minOrderAmount", "expiredAt", "createdAt", "updatedAt"),
                () -> assertThat(response.getBody().data().get("name")).isEqualTo("신규 쿠폰"),
                () -> assertThat(response.getBody().data().get("discountType")).isEqualTo("FIXED")
            );
        }

        @DisplayName("최소 주문 금액이 없는 템플릿은, minOrderAmount가 제약 없음(0)으로 반환된다.")
        @Test
        void returnsOk_withZeroMinOrderAmount() {
            // arrange
            CouponModel savedCoupon = saveCoupon("조건 없는 쿠폰", null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(((Number) response.getBody().data().get("minOrderAmount")).intValue()).isZero()
            );
        }

        @DisplayName("만료 시각이 지난 템플릿도, 200 OK로 정상 조회된다.")
        @Test
        void returnsOk_whenCouponIsExpired() {
            // arrange
            CouponModel expiredCoupon = saveExpiredCoupon("만료 쿠폰");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + expiredCoupon.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().get("name")).isEqualTo("만료 쿠폰")
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponModel savedCoupon = saveCoupon("신규 쿠폰", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedCoupon.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("대상 템플릿이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTargetIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }

    @DisplayName("쿠폰 발급 내역 조회 - GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class ReadCouponIssues {

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
            return (List<Map<String, Object>>) response.getBody().data().get("content");
        }

        private void saveUserCoupon(Long userId, CouponModel coupon) {
            userCouponJpaRepository.save(UserCouponModel.issue(userId, coupon));
        }

        @DisplayName("정상 요청이면, 200 OK와 함께 발급 내역과 페이지 메타가 반환된다.")
        @Test
        void returnsOk_withIssuesAndMeta() {
            // arrange
            CouponModel coupon = saveCoupon("신규 가입 쿠폰", 10_000);
            saveUserCoupon(100L, coupon);
            saveUserCoupon(200L, coupon);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + coupon.getId() + "/issues?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            List<Map<String, Object>> content = contentOf(response);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsKeys("content", "page", "size", "totalElements", "totalPages"),
                () -> assertThat(content).hasSize(2),
                () -> assertThat(content.get(0)).containsOnlyKeys("userCouponId", "userId", "status", "issuedAt"),
                () -> assertThat(content)
                    .extracting(issue -> ((Number) issue.get("userId")).longValue())
                    .containsExactlyInAnyOrder(100L, 200L),
                () -> assertThat(content)
                    .extracting(issue -> issue.get("status"))
                    .containsOnly("AVAILABLE")
            );
        }

        @DisplayName("발급분이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent() {
            // arrange
            CouponModel coupon = saveCoupon("신규 가입 쿠폰", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + coupon.getId() + "/issues",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(contentOf(response)).isEmpty(),
                () -> assertThat(((Number) response.getBody().data().get("size")).intValue()).isEqualTo(20)
            );
        }

        @DisplayName("admin 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            CouponModel coupon = saveCoupon("신규 가입 쿠폰", 10_000);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + coupon.getId() + "/issues",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("대상 템플릿이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTemplateIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999/issues",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("대상 템플릿이 삭제됐으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTemplateIsDeleted() {
            // arrange
            CouponModel coupon = saveCoupon("신규 가입 쿠폰", 10_000);
            saveUserCoupon(100L, coupon);
            coupon.delete();
            couponJpaRepository.saveAndFlush(coupon);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + coupon.getId() + "/issues",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }
}
