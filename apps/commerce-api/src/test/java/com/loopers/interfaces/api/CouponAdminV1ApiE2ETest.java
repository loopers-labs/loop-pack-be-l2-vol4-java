package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/coupons";
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.now().plusDays(30);

    @Autowired
    private TestRestTemplate testRestTemplate;

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

    private UUID createTemplate(String name, CouponType type, Long value, Long minOrderAmount) {
        ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
            BASE_URL, HttpMethod.POST,
            new HttpEntity<>(new CouponV1Dto.CreateRequest(name, type, value, minOrderAmount, EXPIRED_AT), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().data().id();
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class Create {

        @DisplayName("유효한 요청으로 생성 시, 200 + 템플릿 정보를 반환한다.")
        @Test
        void returnsTemplate_whenValidRequest() {
            ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new CouponV1Dto.CreateRequest("신규가입 10% 할인", CouponType.RATE, 10L, 10000L, EXPIRED_AT), adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.RATE),
                () -> assertThat(response.getBody().data().value()).isEqualTo(10L),
                () -> assertThat(response.getBody().data().minOrderAmount()).isEqualTo(10000L)
            );
        }

        @DisplayName("관리자 헤더가 없으면, 401 을 반환한다.")
        @Test
        void throwsUnauthorized_whenNoAdminHeader() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new CouponV1Dto.CreateRequest("쿠폰", CouponType.FIXED, 3000L, null, EXPIRED_AT)),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("RATE 값이 100 을 초과하면, 400 을 반환한다.")
        @Test
        void throwsBadRequest_whenRateOutOfRange() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new CouponV1Dto.CreateRequest("쿠폰", CouponType.RATE, 200L, null, EXPIRED_AT), adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이미 존재하는 쿠폰명으로 생성 시, 409 를 반환한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            createTemplate("중복쿠폰", CouponType.FIXED, 3000L, null);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new CouponV1Dto.CreateRequest("중복쿠폰", CouponType.FIXED, 5000L, null, EXPIRED_AT), adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{id}")
    @Nested
    class Get {

        @DisplayName("존재하는 템플릿 조회 시, 200 + 정보를 반환한다.")
        @Test
        void returnsTemplate_whenExists() {
            UUID id = createTemplate("조회쿠폰", CouponType.FIXED, 3000L, null);

            ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(id),
                () -> assertThat(response.getBody().data().name()).isEqualTo("조회쿠폰")
            );
        }

        @DisplayName("존재하지 않는 ID 조회 시, 404 를 반환한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetList {

        @DisplayName("목록 조회 시, 200 + 페이징 결과를 반환한다.")
        @Test
        void returnsPagedList_whenTemplatesExist() {
            createTemplate("쿠폰A", CouponType.FIXED, 1000L, null);
            createTemplate("쿠폰B", CouponType.FIXED, 2000L, null);
            createTemplate("쿠폰C", CouponType.RATE, 10L, null);

            ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.TemplateResponse>>> response = testRestTemplate.exchange(
                BASE_URL + "?page=0&size=2", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().getContent()).hasSize(2)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{id}")
    @Nested
    class Update {

        @DisplayName("유효한 요청으로 수정 시, 200 + 변경된 정보를 반환한다.")
        @Test
        void returnsUpdated_whenValidRequest() {
            UUID id = createTemplate("수정전", CouponType.RATE, 10L, null);

            ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(new CouponV1Dto.UpdateRequest("수정후", CouponType.FIXED, 5000L, 20000L, EXPIRED_AT), adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정후"),
                () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(response.getBody().data().value()).isEqualTo(5000L)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{id}")
    @Nested
    class Delete {

        @DisplayName("삭제 후 동일 이름 재등록 시, 409 를 반환한다. (영구 차단)")
        @Test
        void throwsConflict_whenDeletedNameReused() {
            UUID id = createTemplate("삭제쿠폰", CouponType.FIXED, 3000L, null);

            ResponseEntity<ApiResponse<Void>> deleteResponse = testRestTemplate.exchange(
                BASE_URL + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<Void>> recreate = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new CouponV1Dto.CreateRequest("삭제쿠폰", CouponType.FIXED, 3000L, null, EXPIRED_AT), adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(recreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
