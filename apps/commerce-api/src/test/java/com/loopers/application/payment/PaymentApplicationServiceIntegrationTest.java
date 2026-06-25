package com.loopers.application.payment;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PaymentApplicationServiceIntegrationTest {

    static final String FAKE_TRANSACTION_KEY = "20250816:TR:test01";

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
            };
        }
    }

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 요청하면, PENDING 결제가 생성되고 PG 트랜잭션 키가 반영된다.")
    @Test
    void requestsPayment_returnsPendingWithTransactionKey() {
        // Arrange
        Member member = memberService.register("payUser1", "Password1!", "홍길동", "1990-01-01", "pay1@example.com");
        Order order = orderRepository.save(Order.create(member.getId(), 5000L, 0L, null));

        // Act
        PaymentInfo info = paymentApplicationService.requestPayment(
            "payUser1", order.getId(), CardType.SAMSUNG, "1234-5678-9814-1451");

        // Assert
        assertThat(info.paymentId()).isNotNull();
        assertThat(info.status()).isEqualTo(PaymentStatus.PENDING);

        Payment saved = paymentService.getPayment(info.paymentId());
        assertThat(saved.getTransactionKey()).isEqualTo(FAKE_TRANSACTION_KEY);
        assertThat(saved.getOrderId()).isEqualTo(order.getId());
    }

    @DisplayName("본인의 주문이 아니면, FORBIDDEN 예외가 발생한다.")
    @Test
    void throwsForbidden_whenNotOwnOrder() {
        // Arrange
        Member owner = memberService.register("owner1", "Password1!", "주인", "1990-01-01", "owner1@example.com");
        Member other = memberService.register("other1", "Password1!", "타인", "1990-01-01", "other1@example.com");
        Order order = orderRepository.save(Order.create(owner.getId(), 5000L, 0L, null));

        // Act
        CoreException ex = assertThrows(CoreException.class,
            () -> paymentApplicationService.requestPayment(
                "other1", order.getId(), CardType.SAMSUNG, "1234-5678-9814-1451"));

        // Assert
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
    }

    @DisplayName("존재하지 않는 주문이면, NOT_FOUND 예외가 발생한다.")
    @Test
    void throwsNotFound_whenOrderMissing() {
        // Arrange
        memberService.register("payUser2", "Password1!", "홍길동", "1990-01-01", "pay2@example.com");

        // Act
        CoreException ex = assertThrows(CoreException.class,
            () -> paymentApplicationService.requestPayment(
                "payUser2", 999_999L, CardType.SAMSUNG, "1234-5678-9814-1451"));

        // Assert
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }
}
