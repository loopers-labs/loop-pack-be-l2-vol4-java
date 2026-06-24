package com.loopers.interfaces.api;

import com.loopers.domain.pg.PgGateway;
import com.loopers.domain.pg.PgTransactionResult;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String USERS_URL         = "/api/v1/users";
    private static final String BRANDS_URL         = "/api-admin/v1/brands";
    private static final String PRODUCTS_URL       = "/api-admin/v1/products";
    private static final String ORDERS_URL         = "/api/v1/orders";
    private static final String PAYMENTS_URL       = "/api/v1/payments";
    private static final String PAYMENTS_CALLBACK_URL = "/api/v1/payments/callback";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @MockBean
    private PgGateway pgGateway;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;
    private UUID brandId;

    @BeforeEach
    void setUp() {
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> userResp = testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<>() {}
        );
        userId = userResp.getBody().data().id();

        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            BRANDS_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        brandId = brandResp.getBody().data().id();

        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> productResp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            ), adminHeaders()),
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private HttpHeaders orderHeaders() {
        HttpHeaders headers = authHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }

    private UUID createOrder() {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", "101호"
        );
        OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
            shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, 2))
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> resp = testRestTemplate.exchange(
            ORDERS_URL, HttpMethod.POST,
            new HttpEntity<>(request, orderHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    private Long getOrderPgAmount(UUID orderId) {
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> resp = testRestTemplate.exchange(
            ORDERS_URL + "/" + orderId, HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().pgAmount();
    }

    @DisplayName("POST /api/v1/payments — 결제 요청")
    @Nested
    class Pay {

        @DisplayName("PENDING 주문에 결제 요청하면, 200 + transactionKey를 반환한다.")
        @Test
        void requestsPayment_whenOrderIsPending() {
            // arrange
            String expectedKey = "20260624:TR:abc123";
            given(pgGateway.request(any(), any(), any(), any(), any(), any()))
                .willReturn(new PgTransactionResult(expectedKey, "PENDING", null));
            UUID orderId = createOrder();

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PayRequest(orderId, "SAMSUNG", "1234-5678-9814-1451"), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().transactionKey()).isEqualTo(expectedKey)
            );
        }

        @DisplayName("인증 없이 요청하면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNotAuthenticated() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PayRequest(UUID.randomUUID(), "SAMSUNG", "1234-5678-9814-1451")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/payments/callback — 결제 결과 콜백")
    @Nested
    class PaymentCallback {

        @DisplayName("SUCCESS 콜백이면, 200 + 주문이 CONFIRMED 된다.")
        @Test
        void confirmsOrder_whenStatusIsSuccess() {
            // arrange
            UUID orderId = createOrder();
            Long amount = getOrderPgAmount(orderId);
            PaymentV1Dto.CallbackPayload payload = new PaymentV1Dto.CallbackPayload(
                "20260624:TR:abc123", orderId.toString(), "SAMSUNG", "1234-5678-9814-1451",
                amount, "SUCCESS", "정상 승인되었습니다."
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_CALLBACK_URL, HttpMethod.POST,
                new HttpEntity<>(payload),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResp = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResp.getBody().data().status().name()).isEqualTo("CONFIRMED");
        }

        @DisplayName("FAILED 콜백이면, 200 + 주문이 FAILED 된다.")
        @Test
        void failsOrder_whenStatusIsFailed() {
            // arrange
            UUID orderId = createOrder();
            Long amount = getOrderPgAmount(orderId);
            PaymentV1Dto.CallbackPayload payload = new PaymentV1Dto.CallbackPayload(
                "20260624:TR:abc123", orderId.toString(), "SAMSUNG", "1234-5678-9814-1451",
                amount, "FAILED", "한도초과입니다."
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_CALLBACK_URL, HttpMethod.POST,
                new HttpEntity<>(payload),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResp = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResp.getBody().data().status().name()).isEqualTo("FAILED");
        }

        @DisplayName("금액 불일치 SUCCESS 콜백이면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenAmountMismatch() {
            // arrange
            UUID orderId = createOrder();
            PaymentV1Dto.CallbackPayload payload = new PaymentV1Dto.CallbackPayload(
                "20260624:TR:abc123", orderId.toString(), "SAMSUNG", "1234-5678-9814-1451",
                1L, "SUCCESS", "정상 승인되었습니다."
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_CALLBACK_URL, HttpMethod.POST,
                new HttpEntity<>(payload),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
