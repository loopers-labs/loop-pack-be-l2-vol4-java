package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentRecoveryIntegrationTest {

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        PaymentGateway fakePaymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentGatewayResult requestPayment(PaymentGatewayCommand command) {
                    return new PaymentGatewayResult("TR:recovery", PaymentStatus.PENDING, null);
                }

                @Override
                public PaymentGatewayResult findTransaction(String userId, String transactionKey) {
                    // 폴링 조회 시 PG가 최종 성공 결과를 반환하는 상황
                    return new PaymentGatewayResult(transactionKey, PaymentStatus.SUCCESS, "정상 승인되었습니다.");
                }

                @Override
                public java.util.List<PaymentGatewayResult> findTransactionsByOrder(String userId, String orderId) {
                    // orderCode로 조회 시 PG에 접수된 결제건이 있는 상황
                    return java.util.List.of(
                        new PaymentGatewayResult("TR:found", PaymentStatus.SUCCESS, "정상 승인되었습니다."));
                }
            };
        }
    }

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("콜백이 유실된 PENDING 결제를, 폴링 복구가 PG 조회로 SUCCESS 확정한다.")
    @Test
    void recoversPendingPayment_viaPolling() {
        // Arrange: PENDING + transactionKey 있는 결제 (콜백 미수신 상황)
        Payment payment = paymentService.initiate(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
        paymentService.assignTransactionKey(payment.getId(), "TR:recovery");

        // Act
        paymentApplicationService.recoverPendingPayments();

        // Assert
        assertThat(paymentService.getPayment(payment.getId()).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @DisplayName("트랜잭션 키가 없는(fallback된) PENDING 결제를, 주문번호로 PG 조회해 키·상태를 복구한다.")
    @Test
    void recoversUnconfirmedRequest_viaOrderCode() {
        // Arrange: 주문은 있으나 결제 요청이 fallback돼 transactionKey가 없는 PENDING
        Order order = orderRepository.save(Order.create(1L, 5000L, 0L, null));
        Payment payment = paymentService.initiate(order.getId(), 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

        // Act
        paymentApplicationService.recoverUnconfirmedRequests();

        // Assert
        Payment found = paymentService.getPayment(payment.getId());
        assertThat(found.getTransactionKey()).isEqualTo("TR:found");
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
