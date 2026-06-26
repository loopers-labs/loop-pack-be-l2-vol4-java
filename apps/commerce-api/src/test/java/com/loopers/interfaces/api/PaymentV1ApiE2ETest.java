package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegistrationCommand;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.payment.PgFeignClient;
import com.loopers.infrastructure.payment.PgV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String LOGIN_ID = "user123";
    private static final String PASSWORD = "Pass1234!";
    private static final String CARD_NO = "1234-5678-9814-1451";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserService userService;
    @Autowired private BrandApplicationService brandApplicationService;
    @Autowired private ProductApplicationService productApplicationService;
    @Autowired private OrderApplicationService orderApplicationService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PgClient pgClient;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @MockBean private PgFeignClient pgFeignClient;

    private Long userId;
    private Long productId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("pg").reset();

        UserModel user = userService.register(
            new UserRegistrationCommand(LOGIN_ID, PASSWORD, "홍길동", "19900101", "hong@example.com"));
        userId = user.getId();

        BrandInfo brand = brandApplicationService.create("나이키", "스포츠 브랜드");
        ProductAdminInfo product = productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 1_000L, 10);
        productId = product.id();

        OrderInfo order = orderApplicationService.createOrder(userId,
            new OrderCommand(List.of(new OrderCommand.Item(productId, 5)), null)); // 재고 10 → 5, total 5000
        orderId = order.orderId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        circuitBreakerRegistry.circuitBreaker("pg").reset();
    }

    private static Request feignRequest() {
        return Request.create(Request.HttpMethod.POST, "http://localhost:8082/api/v1/payments",
            Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<com.loopers.interfaces.api.payment.PaymentV1Dto.PaymentResponse>> pay() {
        var request = new com.loopers.interfaces.api.payment.PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, CARD_NO);
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {});
    }

    private void sendCallback(String transactionKey, String status, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("transactionKey", transactionKey);
        body.put("status", status);
        body.put("reason", reason);
        testRestTemplate.exchange(ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(body, headers),
            new ParameterizedTypeReference<ApiResponse<Object>>() {});
    }

    @Test
    @DisplayName("정상 결제 요청: 접수되어 PENDING + 거래키로 응답한다")
    void requestPayment_accepted() {
        // arrange
        when(pgFeignClient.requestPayment(anyString(), any()))
            .thenReturn(ApiResponse.success(new PgV1Dto.TransactionResponse("TR:1", "PENDING", null)));

        // act
        var response = pay();

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo("PENDING");
        assertThat(response.getBody().data().transactionKey()).isEqualTo("TR:1");
        PaymentModel saved = paymentRepository.findByOrderId(orderId).get(0);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAmount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("콜백 SUCCESS: 주문이 PAID 로 확정된다")
    void callbackSuccess_marksOrderPaid() {
        // arrange
        when(pgFeignClient.requestPayment(anyString(), any()))
            .thenReturn(ApiResponse.success(new PgV1Dto.TransactionResponse("TR:1", "PENDING", null)));
        pay();

        // act
        sendCallback("TR:1", "SUCCESS", null);

        // assert
        assertThat(paymentRepository.findByOrderId(orderId).get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(orderApplicationService.getOrderForAdmin(orderId).status()).isEqualTo(OrderStatus.PAID.name());
    }

    @Test
    @DisplayName("콜백 FAILED: 주문이 취소되고 재고가 복원된다")
    void callbackFailed_cancelsOrderAndRestoresStock() {
        // arrange
        when(pgFeignClient.requestPayment(anyString(), any()))
            .thenReturn(ApiResponse.success(new PgV1Dto.TransactionResponse("TR:1", "PENDING", null)));
        pay();
        assertThat(productRepository.find(productId).orElseThrow().getStock()).isEqualTo(5); // 주문으로 차감됨

        // act
        sendCallback("TR:1", "FAILED", "한도 초과");

        // assert
        assertThat(paymentRepository.findByOrderId(orderId).get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderApplicationService.getOrderForAdmin(orderId).status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(productRepository.find(productId).orElseThrow().getStock()).isEqualTo(10); // 복원됨
    }

    @Test
    @DisplayName("PG 요청 실패(5xx): 내부 시스템은 500이 아닌 정상 응답하며 결제는 PENDING 으로 보존된다")
    void pgFailure_internalStaysResponsive() {
        // arrange
        when(pgFeignClient.requestPayment(anyString(), any()))
            .thenThrow(new FeignException.InternalServerError("PG 불안정", feignRequest(), null, null));

        // act
        var response = pay();

        // assert: 폴백 → PgClientException → Facade 가 흡수 → 정상 200 + PENDING
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo("PENDING");
        assertThat(response.getBody().data().transactionKey()).isNull();
        assertThat(paymentRepository.findByOrderId(orderId).get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("타임아웃 복구: 거래키 없는 PENDING 을 orderId 조회로 재조정해 PAID 로 반영한다")
    void reconcileByOrderId_recoversTimedOutPayment() {
        // arrange: 요청 실패로 거래키 없는 PENDING 생성
        when(pgFeignClient.requestPayment(anyString(), any()))
            .thenThrow(new FeignException.InternalServerError("PG 불안정", feignRequest(), null, null));
        pay();
        Long paymentId = paymentRepository.findByOrderId(orderId).get(0).getId();

        // PG 에는 실제로 성공한 거래가 있었던 상황을 모킹
        when(pgFeignClient.findByOrderId(anyString(), anyString()))
            .thenReturn(ApiResponse.success(new PgV1Dto.OrderResponse(String.format("%06d", orderId),
                List.of(new PgV1Dto.TransactionResponse("TR:found", "SUCCESS", null)))));

        // act: 수동 동기화
        var response = testRestTemplate.exchange(ENDPOINT + "/" + paymentId + "/sync", HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            new ParameterizedTypeReference<ApiResponse<com.loopers.interfaces.api.payment.PaymentV1Dto.PaymentResponse>>() {});

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");
        assertThat(orderApplicationService.getOrderForAdmin(orderId).status()).isEqualTo(OrderStatus.PAID.name());
    }

    @Test
    @DisplayName("서킷브레이커: PG 가 지속 실패하면 회로가 OPEN 되어 빠르게 차단된다")
    void circuitBreaker_opensOnSustainedFailures() {
        // arrange
        when(pgFeignClient.requestPayment(anyString(), any()))
            .thenThrow(new FeignException.InternalServerError("PG 불안정", feignRequest(), null, null));
        PgPaymentCommand command = new PgPaymentCommand(String.valueOf(userId), "000100", CardType.SAMSUNG, CARD_NO, 5000L,
            "http://localhost:8080/api/v1/payments/callback");

        // act: 지속 실패 (minimum-number-of-calls=10, failure-rate-threshold=50%)
        for (int i = 0; i < 15; i++) {
            try {
                pgClient.requestPayment(command);
            } catch (PgClientException ignored) {
                // 폴백이 PgClientException 으로 변환 (회로 차단 포함)
            }
        }

        // assert
        assertThat(circuitBreakerRegistry.circuitBreaker("pg").getState())
            .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);
    }
}
