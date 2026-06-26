package com.loopers.interfaces.api.payment;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.GatewayStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackV1Request;
import com.loopers.interfaces.api.payment.dto.PaymentV1Request;
import com.loopers.interfaces.api.payment.dto.PaymentV1Response;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Pass1234!";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserFacade userFacade;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private Long orderId;
    private Long userId;

    @BeforeEach
    void setUp() {
        userFacade.signUp(LOGIN_ID, PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@loopers.com");
        userId = userFacade.authenticate(LOGIN_ID, PASSWORD);
        OrderModel order = orderRepository.save(
            new OrderModel(userId, List.of(new OrderItem(1L, "후드", 50_000L, 1)), null, Money.ZERO));
        orderId = order.getId();

        when(paymentGateway.requestPayment(any())).thenReturn(GatewayResult.accepted("tx-e2e"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    @DisplayName("POST /api/v1/payments — 결제 요청 시")
    @Nested
    class RequestPayment {

        @DisplayName("정상 요청하면 200 OK이고 결제가 PENDING으로 접수되며 거래키가 반환된다")
        @Test
        void returns200_andPending() {
            PaymentV1Request request = new PaymentV1Request(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            ResponseEntity<ApiResponse<PaymentV1Response>> response = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, userHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getBody()).isNotNull();
            PaymentV1Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.orderId()).isEqualTo(orderId),
                () -> assertThat(data.status()).isEqualTo("PENDING"),
                () -> assertThat(data.transactionKey()).isEqualTo("tx-e2e"),
                () -> assertThat(data.amount()).isEqualTo(50_000L)
            );
        }

        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 반환한다")
        @Test
        void returns401_whenAuthHeadersMissing() {
            PaymentV1Request request = new PaymentV1Request(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            ResponseEntity<ApiResponse<PaymentV1Response>> response = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("카드 번호 형식이 올바르지 않으면 400 BAD_REQUEST 를 반환한다")
        @Test
        void returns400_whenCardNoMalformed() {
            PaymentV1Request request = new PaymentV1Request(orderId, CardType.SAMSUNG, "1234");

            ResponseEntity<ApiResponse<PaymentV1Response>> response = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, userHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("같은 주문으로 두 번 연속 요청해도 PG 접수는 한 번만 호출된다 (이중 접수 방지)")
        @Test
        void callsGatewayOnce_whenRequestedTwice() {
            PaymentV1Request request = new PaymentV1Request(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
            HttpEntity<PaymentV1Request> entity = new HttpEntity<>(request, userHeaders());

            restTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<ApiResponse<PaymentV1Response>>() {});
            restTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<ApiResponse<PaymentV1Response>>() {});

            verify(paymentGateway, times(1)).requestPayment(any());
        }
    }

    @DisplayName("POST /api/v1/payments/callback — 결제 콜백 수신 시")
    @Nested
    class HandleCallback {

        @DisplayName("transactionKey가 비어 있으면 400 BAD_REQUEST 를 반환한다")
        @Test
        void returns400_whenTransactionKeyBlank() {
            PaymentCallbackV1Request request = new PaymentCallbackV1Request("  ", "SUCCESS", null);

            ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(request, new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("본문이 SUCCESS를 주장해도 PG 재조회가 FAILED면 결제는 FAILED·주문은 CANCELED로 확정된다 (위조 본문 무시)")
        @Test
        void ignoresForgedSuccessBody_whenRequeryFailed() {
            stockRepository.save(new StockModel(1L, 10));
            PaymentModel payment = new PaymentModel(orderId, userId, CardType.SAMSUNG, Money.of(50_000L));
            payment.assignTransactionKey("tx-forged");
            paymentRepository.save(payment);
            when(paymentGateway.queryStatus("tx-forged", userId)).thenReturn(Optional.of(new GatewayStatus("FAILED", "한도 초과")));

            // 콜백 본문은 SUCCESS라 우긴다 — 컨트롤러/Facade는 본문을 신뢰하지 않고 transactionKey만 쓴다
            PaymentCallbackV1Request forged = new PaymentCallbackV1Request("tx-forged", "SUCCESS", "정상 결제");
            ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(forged, new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED)
            );
        }
    }
}
