package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.pg.PgApiResponse;
import com.loopers.infrastructure.pg.PgFeignClient;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import feign.FeignException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String PAYMENTS_URL = "/api/v1/payments";
    private static final String USERS_URL = "/api/v1/users";
    private static final String ORDERS_URL = "/api/v1/orders";
    private static final String LOGIN_ID = "paymentuser";
    private static final String LOGIN_PW = "pAssWord1!";

    @MockBean
    private PgFeignClient pgFeignClient;

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private ProductStockJpaRepository productStockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        reset(pgFeignClient);

        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.UserJoinRequest(LOGIN_ID, LOGIN_PW, "결제유저", LocalDate.of(2000, 1, 1), "pay@test.com")),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );

        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    private Long createOrder() {
        OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
            List.of(new OrderV1Dto.OrderCreateRequest.Item(productId, 1)), null
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
            ORDERS_URL, HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().orderId();
    }

    private String requestPaymentAndGetTransactionKey(Long orderId) {
        given(pgFeignClient.requestPayment(anyString(), any()))
            .willReturn(new PgApiResponse.Payment("test:TR:abc123", "PENDING", null));

        ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
            PAYMENTS_URL, HttpMethod.POST,
            new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456"), authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().transactionKey();
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(1L, CardType.SAMSUNG, "1234-5678-9012-3456")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 주문이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(9999L, CardType.SAMSUNG, "1234-5678-9012-3456"), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("타 유저의 주문이면, 403을 반환한다.")
        @Test
        void returnsForbidden_whenOrderBelongsToOtherUser() {
            Long orderId = createOrder();

            String otherId = "otheruser";
            testRestTemplate.exchange(
                USERS_URL, HttpMethod.POST,
                new HttpEntity<>(new UserV1Dto.UserJoinRequest(otherId, LOGIN_PW, "타인", LocalDate.of(2000, 1, 1), "other@test.com")),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            );

            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set("X-Loopers-LoginId", otherId);
            otherHeaders.set("X-Loopers-LoginPw", LOGIN_PW);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456"), otherHeaders),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("주문이 이미 결제 완료 처리된 경우 (CONFIRMED 상태), 409를 반환한다.")
        @Test
        void returnsConflict_whenOrderIsAlreadyConfirmed() {
            Long orderId = createOrder();
            String transactionKey = requestPaymentAndGetTransactionKey(orderId);

            testRestTemplate.exchange(
                PAYMENTS_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.CallbackRequest(transactionKey, "SUCCESS", "정상 승인")),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456"), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("PG 결제 요청이 성공하면, 201과 transactionKey를 반환한다.")
        @Test
        void returnsCreatedWithTransactionKey_whenPgSucceeds() {
            Long orderId = createOrder();
            given(pgFeignClient.requestPayment(anyString(), any()))
                .willReturn(new PgApiResponse.Payment("20260624:TR:abc123", "PENDING", null));

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456"), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().transactionKey()).isEqualTo("20260624:TR:abc123")
            );
        }

        @DisplayName("PG 결제 요청이 최종 실패하면, 503을 반환한다.")
        @Test
        void returnsServiceUnavailable_whenPgFails() {
            Long orderId = createOrder();
            given(pgFeignClient.requestPayment(anyString(), any()))
                .willThrow(FeignException.InternalServerError.class);

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456"), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class ReceiveCallback {

        @DisplayName("존재하지 않는 transactionKey이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenTransactionKeyDoesNotExist() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.CallbackRequest("fake:TR:000000", "SUCCESS", "정상 승인")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("SUCCESS 콜백이면, 결제가 성공 처리되고 200을 반환한다.")
        @Test
        void returnsOk_whenSuccessCallback() {
            Long orderId = createOrder();
            String transactionKey = requestPaymentAndGetTransactionKey(orderId);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.CallbackRequest(transactionKey, "SUCCESS", "정상 승인되었습니다.")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("FAILED 콜백이면, 결제가 실패 처리되고 200을 반환한다.")
        @Test
        void returnsOk_whenFailedCallback() {
            Long orderId = createOrder();
            String transactionKey = requestPaymentAndGetTransactionKey(orderId);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.CallbackRequest(transactionKey, "FAILED", "한도초과입니다.")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("이미 처리된 transactionKey이면, 멱등 처리하여 200을 반환한다.")
        @Test
        void returnsOk_whenCallbackIsIdempotent() {
            Long orderId = createOrder();
            String transactionKey = requestPaymentAndGetTransactionKey(orderId);

            testRestTemplate.exchange(
                PAYMENTS_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.CallbackRequest(transactionKey, "SUCCESS", "정상 승인되었습니다.")),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.CallbackRequest(transactionKey, "SUCCESS", "정상 승인되었습니다.")),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}