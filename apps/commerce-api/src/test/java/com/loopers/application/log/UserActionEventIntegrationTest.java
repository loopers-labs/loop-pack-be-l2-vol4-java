package com.loopers.application.log;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentFailedEvent;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentSucceededEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Controller;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 행동 로깅의 전제인 "사실 이벤트가 발행되는가"를 검증한다.
 * 리스너의 로그 출력 자체가 아니라, 각 핵심 흐름이 해당 이벤트를 발행하는지를 본다.
 */
@SpringBootTest
@RecordApplicationEvents
class UserActionEventIntegrationTest {

    private static final String TX = "tx-001";

    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private ProductV1Controller productV1Controller;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private ApplicationEvents events;

    // 주문 시 PG 호출은 막고, 결제는 transactionKey=TX 의 PENDING 으로 생성되게 한다.
    @MockitoBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.requestPayment(any()))
            .willReturn(new PaymentGateway.PaymentResult(TX, PaymentStatus.PENDING, null));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
    }

    @DisplayName("주문을 생성하면, OrderCreatedEvent 가 발행된다.")
    @Test
    void publishesOrderCreatedEvent_whenOrderIsPlaced() {
        Product product = saveProduct();

        OrderInfo info = orderFacade.place(1L, List.of(new OrderLineCommand(product.getId(), 2)), null);

        assertThat(events.stream(OrderCreatedEvent.class))
            .anyMatch(event -> event.orderId().equals(info.id()) && event.userId().equals(1L));
    }

    @DisplayName("결제가 성공으로 확정되면, PaymentSucceededEvent 가 발행된다.")
    @Test
    void publishesPaymentSucceededEvent_whenPaymentConfirmedSuccess() {
        Product product = saveProduct();
        OrderInfo info = orderFacade.place(1L, List.of(new OrderLineCommand(product.getId(), 2)), null);
        paymentFacade.pay(1L, info.id(), CardType.SAMSUNG, "1234-1234-1234-1234");

        paymentFacade.confirm(TX, PaymentStatus.SUCCESS, null);

        assertThat(events.stream(PaymentSucceededEvent.class))
            .anyMatch(event -> event.orderId().equals(info.id()));
    }

    @DisplayName("결제가 실패로 확정되면, PaymentFailedEvent 가 발행된다.")
    @Test
    void publishesPaymentFailedEvent_whenPaymentConfirmedFailed() {
        Product product = saveProduct();
        OrderInfo info = orderFacade.place(1L, List.of(new OrderLineCommand(product.getId(), 2)), null);
        paymentFacade.pay(1L, info.id(), CardType.SAMSUNG, "1234-1234-1234-1234");

        paymentFacade.confirm(TX, PaymentStatus.FAILED, "카드 한도 초과");

        assertThat(events.stream(PaymentFailedEvent.class))
            .anyMatch(event -> event.orderId().equals(info.id()) && event.reason().equals("카드 한도 초과"));
    }

    @DisplayName("상품 상세를 조회하면, ProductViewedEvent 가 발행된다.")
    @Test
    void publishesProductViewedEvent_whenProductIsViewed() {
        Product product = saveProduct();

        productV1Controller.getProduct(product.getId());

        assertThat(events.stream(ProductViewedEvent.class))
            .anyMatch(event -> event.productId().equals(product.getId()));
    }
}
