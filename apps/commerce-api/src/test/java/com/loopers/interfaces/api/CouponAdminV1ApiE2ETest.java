package com.loopers.interfaces.api;

import com.loopers.application.coupon.CouponAdminInfo;
import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private CouponApplicationService couponApplicationService;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private CouponAdminInfo createCoupon(String name, CouponType type, int value) {
        return couponApplicationService.createCoupon(name, type, value, 0, FUTURE);
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 정보로 쿠폰을 등록하면, 200 OK와 등록된 쿠폰 정보를 반환한다.")
        @Test
        void returnsOk_whenValidRequestIsGiven() {
            // arrange
            CouponAdminV1Dto.CreateCouponRequest body = new CouponAdminV1Dto.CreateCouponRequest(
                "신규가입 10% 할인", CouponAdminV1Dto.CouponTypeDto.RATE, 10, 0, FUTURE
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo(CouponAdminV1Dto.CouponTypeDto.RATE),
                () -> assertThat(response.getBody().data().value()).isEqualTo(10)
            );
        }

        @DisplayName("이름이 비어있으면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            CouponAdminV1Dto.CreateCouponRequest body = new CouponAdminV1Dto.CreateCouponRequest(
                "", CouponAdminV1Dto.CouponTypeDto.RATE, 10, 0, FUTURE
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetCoupons {

        @DisplayName("쿠폰 목록을 페이징으로 조회하면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsOk_whenValidPageParamsAreGiven() {
            // arrange
            createCoupon("신규가입 10% 할인", CouponType.RATE, 10);
            createCoupon("5000원 할인", CouponType.FIXED, 5000);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("page가 음수이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPageIsNegative() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=-1", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 0이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeIsZero() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?size=0", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 100을 초과하면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSizeExceedsMax() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "?size=101", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetCoupon {

        @DisplayName("존재하는 쿠폰 id로 요청하면, 200 OK와 쿠폰 정보를 반환한다.")
        @Test
        void returnsOk_whenCouponExists() {
            // arrange
            CouponAdminInfo coupon = createCoupon("신규가입 10% 할인", CouponType.RATE, 10);

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.id(), HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().id()).isEqualTo(coupon.id()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인")
            );
        }

        @DisplayName("존재하지 않는 쿠폰 id로 요청하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateCoupon {

        @DisplayName("존재하는 쿠폰을 수정하면, 200 OK와 수정된 쿠폰 정보를 반환한다.")
        @Test
        void returnsOk_whenCouponExists() {
            // arrange
            CouponAdminInfo coupon = createCoupon("신규가입 10% 할인", CouponType.RATE, 10);
            CouponAdminV1Dto.UpdateCouponRequest body = new CouponAdminV1Dto.UpdateCouponRequest(
                "수정된 쿠폰", CouponAdminV1Dto.CouponTypeDto.FIXED, 3000, 10000, FUTURE
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.id(), HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertAll(
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(response.getBody().data().type()).isEqualTo(CouponAdminV1Dto.CouponTypeDto.FIXED),
                () -> assertThat(response.getBody().data().value()).isEqualTo(3000)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 id로 수정 요청하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // arrange
            CouponAdminV1Dto.UpdateCouponRequest body = new CouponAdminV1Dto.UpdateCouponRequest(
                "수정된 쿠폰", CouponAdminV1Dto.CouponTypeDto.FIXED, 3000, 0, FUTURE
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰을 삭제하면, 200 OK 응답을 반환한다.")
        @Test
        void returnsOk_whenCouponExists() {
            // arrange
            CouponAdminInfo coupon = createCoupon("신규가입 10% 할인", CouponType.RATE, 10);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.id(), HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 쿠폰 id로 삭제 요청하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetCouponIssues {

        @DisplayName("발급 내역이 있는 쿠폰 id로 요청하면, 200 OK와 발급 내역을 반환한다.")
        @Test
        void returnsOk_whenIssuesExist() {
            // arrange
            CouponAdminInfo coupon = createCoupon("신규가입 10% 할인", CouponType.RATE, 10);
            userCouponRepository.save(new UserCouponModel(1L, coupon.id()));

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + coupon.id() + "/issues?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 쿠폰 id로 요청하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        void returnsNotFound_whenCouponDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/999/issues?page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
