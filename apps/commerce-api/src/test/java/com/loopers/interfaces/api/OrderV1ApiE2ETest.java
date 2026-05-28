package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String USERS_URL    = "/api/v1/users";
    private static final String BRANDS_URL   = "/api/v1/admin/brands";
    private static final String PRODUCTS_URL = "/api/v1/admin/products";
    private static final String ORDERS_URL   = "/api/v1/orders";
    private static final String PAYMENTS_CONFIRM_URL = "/api/v1/payments/confirm";
    private static final String PAYMENTS_FAIL_URL    = "/api/v1/payments/fail";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> userResp = testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(userResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        userId = userResp.getBody().data().id();

        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            BRANDS_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION)),
            new ParameterizedTypeReference<>() {}
        );
        UUID brandId = brandResp.getBody().data().id();

        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> productResp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            )),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResp.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private OrderV1Dto.CreateRequest validCreateRequest() {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, 2)));
    }

    @DisplayName("POST /api/v1/orders — 주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면, 200 + PENDING 주문을 반환한다.")
        @Test
        void returnsOrder_whenValid() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("PENDING"),
                () -> assertThat(response.getBody().data().pgAmount()).isEqualTo(ProductFixture.PRICE * 2),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("인증 헤더 없이 요청 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNoAuth() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("재고가 부족하면, 409를 반환한다.")
        @Test
        void returnsConflict_whenStockInsufficient() {
            OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
                new OrderV1Dto.ShippingInfoRequest("홍길동", "010-1234-5678", "12345", "서울시", null),
                List.of(new OrderV1Dto.OrderItemRequest(productId, ProductFixture.INITIAL_QUANTITY + 1))
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(req, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
                new OrderV1Dto.ShippingInfoRequest("홍길동", "010-1234-5678", "12345", "서울시", null),
                List.of(new OrderV1Dto.OrderItemRequest(UUID.randomUUID(), 1))
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(req, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} — 주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 조회 시, 200 + 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId)
            );
        }

        @DisplayName("존재하지 않는 주문 ID면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders — 주문 목록 조회")
    @Nested
    class GetOrderList {

        @DisplayName("본인 주문 목록 조회 시, 200 + 목록을 반환한다.")
        @Test
        void returnsList_whenOwnUser() {
            // arrange
            testRestTemplate.exchange(ORDERS_URL, HttpMethod.POST, new HttpEntity<>(validCreateRequest(), authHeaders()), new ParameterizedTypeReference<>() {});

            String startAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endAt   = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = ORDERS_URL + "?userId=" + userId + "&startAt=" + startAt + "&endAt=" + endAt + "&page=0&size=10";

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(1)
            );
        }

        @DisplayName("타인의 주문 목록 조회 시, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOtherUser() {
            String startAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endAt   = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = ORDERS_URL + "?userId=" + UUID.randomUUID() + "&startAt=" + startAt + "&endAt=" + endAt + "&page=0&size=10";

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel — 주문 취소")
    @Nested
    class CancelOrder {

        @DisplayName("CONFIRMED 주문 취소 시, 200 + CANCELLED 상태를 반환한다.")
        @Test
        void cancelsOrder_whenConfirmed() {
            // arrange — 주문 생성 → 결제 확정
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();

            testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", amount)),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("CANCELLED")
            );
        }

        @DisplayName("PENDING 주문 취소 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPending() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/payments/confirm — 결제 확정")
    @Nested
    class ConfirmPayment {

        @DisplayName("올바른 금액이면, 200 + SUCCESS 결제를 반환한다.")
        @Test
        void confirmsPayment_whenAmountMatches() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", amount)),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("SUCCESS"),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId)
            );
        }

        @DisplayName("금액 불일치이면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenAmountMismatch() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", 1L)),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/payments/fail — 결제 실패")
    @Nested
    class FailPayment {

        @DisplayName("PENDING 주문 결제 실패 시, 200 + FAILED 결제를 반환하고 주문이 FAILED 된다.")
        @Test
        void failsPayment_whenPending() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_FAIL_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.FailRequest(orderId, "pg-tx-fail-001", amount)),
                new ParameterizedTypeReference<>() {}
            );

            // assert — 결제 FAILED, 주문 FAILED 확인
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("FAILED")
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResp = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResp.getBody().data().status().name()).isEqualTo("FAILED");
        }
    }
}
