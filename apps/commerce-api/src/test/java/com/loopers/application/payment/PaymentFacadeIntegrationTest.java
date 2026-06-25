package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.pg.PgCallbackPayload;
import com.loopers.infrastructure.pg.PgPaymentClient;
import com.loopers.infrastructure.pg.PgTransactionResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @MockBean private PgPaymentClient pgPaymentClient;

    private static final Long USER_ID = 1L;

    private OrderModel savedOrder;

    @BeforeEach
    void setUp() {
        OrderModel order = new OrderModel(USER_ID);
        order.applyPricing(10_000, 0);
        savedOrder = orderJpaRepository.save(order);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private PaymentModel savePaymentWithStatus(PaymentStatus status) {
        PaymentModel payment = new PaymentModel(savedOrder.getId(), USER_ID, CardType.SAMSUNG, "1234-5678-9014-1451", 10_000);
        if (status == PaymentStatus.IN_PROGRESS) {
            payment.startProgress("TX-INTEGRATION-123");
        } else if (status == PaymentStatus.ABORTED) {
            payment.markAborted();
        }
        return paymentJpaRepository.save(payment);
    }

    @Nested
    @DisplayName("handleCallback() 통합 테스트")
    class HandleCallback {

        @Test
        @DisplayName("SUCCESS 콜백 수신 시 결제가 SUCCESS, 주문이 CONFIRMED로 DB에 반영된다.")
        void persistsSuccessAndConfirmedOrder_whenCallbackIsSuccess() {
            // arrange
            PaymentModel payment = savePaymentWithStatus(PaymentStatus.IN_PROGRESS);

            // act
            paymentFacade.handleCallback(
                new PgCallbackPayload("TX-INTEGRATION-123", null, "SUCCESS", null)
            );

            // assert
            PaymentModel updatedPayment = paymentJpaRepository.findById(payment.getId()).orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(savedOrder.getId()).orElseThrow();

            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("FAILED 콜백 수신 시 결제가 FAILED로 DB에 반영되고 failureCode가 저장된다.")
        void persistsFailedWithCode_whenCallbackIsFailed() {
            // arrange
            PaymentModel payment = savePaymentWithStatus(PaymentStatus.IN_PROGRESS);

            // act
            paymentFacade.handleCallback(
                new PgCallbackPayload("TX-INTEGRATION-123", null, "FAILED", "INVALID_CARD")
            );

            // assert
            PaymentModel updatedPayment = paymentJpaRepository.findById(payment.getId()).orElseThrow();

            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(updatedPayment.getFailureCode()).isEqualTo("INVALID_CARD");
        }
    }

    @Nested
    @DisplayName("syncPayment() 통합 테스트")
    class SyncPayment {

        @Test
        @DisplayName("ABORTED 결제에서 PG SUCCESS 응답 수신 시 SUCCESS로 복구되고 주문이 확정된다.")
        void recoversToSuccess_whenAbortedAndPgSucceeds() {
            // arrange
            PaymentModel payment = savePaymentWithStatus(PaymentStatus.ABORTED);
            String paddedOrderId = String.format("%010d", savedOrder.getId());
            given(pgPaymentClient.findByOrderId(paddedOrderId, USER_ID))
                .willReturn(Optional.of(new PgTransactionResponse("TX-RECOVERED", "SUCCESS", null)));

            // act
            PaymentInfo result = paymentFacade.syncPayment(payment.getId(), USER_ID);

            // assert
            PaymentModel updatedPayment = paymentJpaRepository.findById(payment.getId()).orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(savedOrder.getId()).orElseThrow();

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(updatedPayment.getPgTransactionId()).isEqualTo("TX-RECOVERED");
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("IN_PROGRESS 결제에서 PG 기록이 없으면 상태가 유지된다.")
        void maintainsInProgress_whenPgHasNoRecord() {
            // arrange
            PaymentModel payment = savePaymentWithStatus(PaymentStatus.IN_PROGRESS);
            given(pgPaymentClient.getStatus(anyString(), anyLong())).willReturn(Optional.empty());

            // act
            PaymentInfo result = paymentFacade.syncPayment(payment.getId(), USER_ID);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("이미 SUCCESS인 결제는 PG 조회 없이 현재 상태를 반환한다.")
        void returnsCurrentStatus_whenAlreadySuccess() {
            // arrange — handleCallback으로 SUCCESS 상태로 만든 후 sync 호출
            PaymentModel payment = savePaymentWithStatus(PaymentStatus.IN_PROGRESS);
            paymentFacade.handleCallback(
                new PgCallbackPayload("TX-INTEGRATION-123", null, "SUCCESS", null)
            );

            // act
            PaymentInfo result = paymentFacade.syncPayment(payment.getId(), USER_ID);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }
}
