package com.loopers.interfaces.api;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.brand.BrandFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Abcd1234!";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderFacade orderFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private Long orderId;

    @Autowired
    public PaymentV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderFacade orderFacade,
        OrderJpaRepository orderJpaRepository,
        PaymentJpaRepository paymentJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderFacade = orderFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        Long userId = userFacade.signUp(new UserCommand.SignUp(
            LOGIN_ID, LOGIN_PW, "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
        )).id();
        OrderInfo order = orderFacade.placeOrder(userId, new OrderCommand.Place(List.of(
            new OrderCommand.Line(productId, 2)
        )));
        orderId = order.id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        return headers;
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class Pay {

        @DisplayName("PG 가 접수증(PENDING+key)을 응답하면, 200 으로 접수 결과를 반환하고 주문은 PAYMENT_PENDING, 결제는 PENDING+key 로 적재된다.")
        @Test
        void returnsPendingAck_andPersistsState_whenPgAcknowledges() {
            // given
            given(paymentGateway.requestPayment(any(PaymentGatewayCommand.class)))
                .willReturn(new PaymentGatewayResult("20260624:TR:abc123", PaymentStatus.PENDING));
            PaymentV1Dto.PaymentRequest request =
                new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9814-1451");

            // when
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType
            );

            // then
            Long paymentId = response.getBody().data().paymentId();
            PaymentModel payment = paymentJpaRepository.findById(paymentId).orElseThrow();
            ArgumentCaptor<PaymentGatewayCommand> captor = ArgumentCaptor.forClass(PaymentGatewayCommand.class);
            verify(paymentGateway).requestPayment(captor.capture());
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(response.getBody().data().transactionKey()).isEqualTo("20260624:TR:abc123"),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                // 결제 상태 적재
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("20260624:TR:abc123"),
                // 주문 상태 전이
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING),
                // 서버측 금액(주문 finalAmount)으로 PG 호출 — 클라이언트가 보낸 값이 아님
                () -> assertThat(captor.getValue().amount()).isEqualTo(200_000L)
            );
        }

        @DisplayName("인증 헤더가 누락되면, UNAUTHORIZED 를 반환하고 PG 를 호출하지 않는다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // given
            PaymentV1Dto.PaymentRequest request =
                new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9814-1451");

            // when
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, new HttpHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(paymentJpaRepository.count()).isZero()
            );
        }
    }
}
