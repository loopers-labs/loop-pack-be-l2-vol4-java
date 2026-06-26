package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 서킷 OPEN으로 PG 접수가 거절(REJECTED)되면 — PENDING 방치가 아니라 — 즉시 실패 처리되어 주문이 취소되는지 검증한다. */
@SpringBootTest
class PaymentRequestRejectionIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private PaymentFacade paymentFacade;
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

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("서킷 OPEN으로 접수가 REJECTED되면 결제는 FAILED·주문은 CANCELED로 확정되고 재고가 복원된다")
    @Test
    void rejectedByOpenCircuit_failsPaymentAndCancelsOrder() {
        // given — CREATED 주문 + 재고, PG는 서킷 OPEN으로 거절(REJECTED) 반환
        stockRepository.save(new StockModel(PRODUCT_ID, 10));
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(PRODUCT_ID, "후드", 50_000L, 1)), null, Money.ZERO));
        when(paymentGateway.requestPayment(any())).thenReturn(GatewayResult.rejected());

        // when
        paymentFacade.requestPayment(USER_ID, order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456");

        // then — PENDING 방치가 아니라 즉시 실패 + 주문 취소 + 재고 복원
        assertAll(
            () -> assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED),
            () -> assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED),
            () -> assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(11)
        );
    }
}
