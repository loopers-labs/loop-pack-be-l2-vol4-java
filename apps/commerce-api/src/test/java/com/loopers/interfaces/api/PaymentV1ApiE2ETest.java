package com.loopers.interfaces.api;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String ENDPOINT_CALLBACK = "/api/v1/payments/callback";
    private static final String FAKE_TRANSACTION_KEY = "20260625:TR:e2e001";

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        PaymentGateway fakePaymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentGatewayResult requestPayment(PaymentGatewayCommand command) {
                    return new PaymentGatewayResult(FAKE_TRANSACTION_KEY, PaymentStatus.PENDING, null);
                }

                @Override
                public PaymentGatewayResult findTransaction(String userId, String transactionKey) {
                    return new PaymentGatewayResult(transactionKey, PaymentStatus.PENDING, null);
                }

                @Override
                public List<PaymentGatewayResult> findTransactionsByOrder(String userId, String orderId) {
                    return List.of();
                }
            };
        }
    }

    private final TestRestTemplate testRestTemplate;
    private final MemberService memberService;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public PaymentV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberService memberService,
        OrderRepository orderRepository,
        PaymentService paymentService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberService = memberService;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 요청하고 콜백으로 확정하는 전체 흐름이 동작한다.")
    @Test
    void paymentRequestThenCallback_endToEnd() {
        // Arrange: 회원 + 주문 시드
        Member member = memberService.register("e2eUser", "Password1!", "홍길동", "1990-01-01", "e2e@example.com");
        Order order = orderRepository.save(Order.create(member.getId(), 5000L, 0L, null));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "e2eUser");
        PaymentV1Dto.PaymentRequest body = new PaymentV1Dto.PaymentRequest(
            order.getId(), CardType.SAMSUNG, "1234-5678-9814-1451");

        // Act 1: 결제 요청 (POST /payments)
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PAYMENTS, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);

        // Assert 1: 202 ACCEPTED + PENDING
        Long paymentId = response.getBody().data().paymentId();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED),
            () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING),
            () -> assertThat(paymentId).isNotNull()
        );

        // Act 2: PG 콜백 (POST /payments/callback) - 성공 통지
        PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest(
            FAKE_TRANSACTION_KEY, String.valueOf(order.getOrderCode()), "SAMSUNG",
            "1234-5678-9814-1451", 5000L, PaymentStatus.SUCCESS, "정상 승인되었습니다.");
        ParameterizedTypeReference<ApiResponse<Object>> callbackType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Object>> callbackResponse = testRestTemplate.exchange(
            ENDPOINT_CALLBACK, HttpMethod.POST, new HttpEntity<>(callback), callbackType);

        // Assert 2: 콜백 200 + 결제 SUCCESS 확정
        assertAll(
            () -> assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(paymentService.getPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.SUCCESS)
        );
    }
}
