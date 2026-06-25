package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentCallbackV1ApiE2ETest {

    private static final String CALLBACK_ENDPOINT = "/api/v1/payments/callback";
    private static final String TRANSACTION_KEY = "20260624:TR:abc123";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderFacade orderFacade;
    private final PaymentFacade paymentFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private Long orderId;
    private Long paymentId;

    @Autowired
    public PaymentCallbackV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderFacade orderFacade,
        PaymentFacade paymentFacade,
        OrderJpaRepository orderJpaRepository,
        PaymentJpaRepository paymentJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderFacade = orderFacade;
        this.paymentFacade = paymentFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        Long userId = userFacade.signUp(new UserCommand.SignUp(
            "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
        )).id();
        OrderInfo order = orderFacade.placeOrder(userId, new OrderCommand.Place(List.of(
            new OrderCommand.Line(productId, 2)
        )));
        orderId = order.id();

        // 결제 접수(PENDING + transactionKey) — 외부 PG 는 stub
        given(paymentGateway.requestPayment(any())).willReturn(new PaymentGatewayResult(TRANSACTION_KEY, PaymentStatus.PENDING));
        PaymentInfo paid = paymentFacade.pay(userId, new PaymentCommand.Pay(orderId, "SAMSUNG", "1234-5678-9814-1451"));
        paymentId = paid.paymentId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ResponseEntity<ApiResponse<Object>> postCallback(String status, String reason) {
        PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest(
            TRANSACTION_KEY, "000100", "SAMSUNG", "1234-5678-9814-1451", 200_000L, status, reason);
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            CALLBACK_ENDPOINT, HttpMethod.POST, new HttpEntity<>(callback), responseType);
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {

        @DisplayName("SUCCESS 콜백이 오면, 결제는 SUCCESS, 주문은 PAID 로 확정된다.")
        @Test
        void confirmsPaidOnSuccess() {
            // when
            ResponseEntity<ApiResponse<Object>> response = postCallback("SUCCESS", "정상 승인되었습니다.");

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("FAILED 콜백이 오면, 결제는 FAILED, 주문은 PAYMENT_FAILED 로 확정된다.")
        @Test
        void confirmsFailedOnFailure() {
            // when
            ResponseEntity<ApiResponse<Object>> response = postCallback("FAILED", "한도초과입니다.");

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("같은 SUCCESS 콜백이 두 번 와도(중복 수신), 멱등하게 SUCCESS/PAID 를 유지한다.")
        @Test
        void isIdempotent_onDuplicateCallback() {
            // when
            postCallback("SUCCESS", "정상 승인되었습니다.");
            ResponseEntity<ApiResponse<Object>> second = postCallback("SUCCESS", "정상 승인되었습니다.");

            // then
            assertAll(
                () -> assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }
    }
}
