package com.loopers.application.payment;

import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgIndeterminateException;
import com.loopers.domain.payment.PgRequestRejectedException;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 비동기 PG 결제 요청(requestPayment) 분기 통합 테스트.
 *
 * <p>{@link FakePgGateway} 로 PG 응답을 제어하고, 실제 DB 흐름 위에서 세 갈래를 검증한다:
 * <ul>
 *   <li>정상 → 주문 PAYMENT_IN_PROGRESS 유지 + 결제 REQUESTED(TID 보존) + 자원 점유 유지</li>
 *   <li>{@link PgRequestRejectedException}(500 소진) → 결제·주문 FAILED + 자원 복구</li>
 *   <li>{@link PgIndeterminateException}(타임아웃/서킷) → 실패 처리 금지, PAYMENT_IN_PROGRESS 유지</li>
 * </ul>
 */
@SpringBootTest
@Import(PaymentRequestIntegrationTest.FakeGatewayConfig.class)
class PaymentRequestIntegrationTest {

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        FakePgGateway fakePgGateway() {
            return new FakePgGateway();
        }
    }

    @Autowired private PaymentApplicationService paymentApplicationService;
    @Autowired private OrderTransactionService orderTransactionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private FakePgGateway fakePgGateway;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void resetGateway() {
        fakePgGateway.requestBehavior = () -> "FAKE-TX-DEFAULT";
        fakePgGateway.transactions = List.of();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel givenProductWithStock(int stockQuantity) {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), stockQuantity));
        return product;
    }

    private OrderModel givenPendingOrder(Long userId, ProductModel product, int quantity) {
        return orderTransactionService.createPendingOrder(
            userId, List.of(new OrderItemCommand(product.getId(), quantity)), null);
    }

    private int stockOf(ProductModel product) {
        return stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity();
    }

    @DisplayName("PG 요청 성공 시 transactionKey 를 반환하고 주문은 PAYMENT_IN_PROGRESS, 결제는 REQUESTED(TID 보존)로 남는다.")
    @Test
    void returnsKeyAndKeepsInProgress_whenPgRequestSucceeds() {
        // arrange — 재고 10, PENDING 견적
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 2);
        fakePgGateway.requestBehavior = () -> "TX-SUCCESS-123";

        // act
        PaymentRequestInfo result = paymentApplicationService.requestPayment(
            1L, pending.getId(), CardType.SAMSUNG, "1234-5678-1234-5678", pending.getTotalPrice());

        // assert — 콜백 대기 상태. 자원은 점유(차감)된 채 유지
        assertThat(result.transactionKey()).isEqualTo("TX-SUCCESS-123");
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        PaymentModel payment = paymentRepository.findByOrderId(pending.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getPgTransactionId()).isEqualTo("TX-SUCCESS-123");
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @DisplayName("PG 요청 거부(500 소진)면 INTERNAL_ERROR 로 실패하고, 결제·주문 FAILED + 점유 자원이 복구된다.")
    @Test
    void failsAndReleases_whenPgRequestRejected() {
        // arrange — 재고 10, PENDING 견적
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 2);
        fakePgGateway.requestBehavior = () -> {
            throw new PgRequestRejectedException("500 소진", null);
        };

        // act
        CoreException ex = assertThrows(CoreException.class, () ->
            paymentApplicationService.requestPayment(
                1L, pending.getId(), CardType.SAMSUNG, "1234-5678-1234-5678", pending.getTotalPrice()));

        // assert — 확정 실패: 자원 원복 + 주문/결제 FAILED
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
        assertThat(paymentRepository.findByOrderId(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @DisplayName("PG 결과 미확정(타임아웃/서킷)이면 실패 처리하지 않고 PAYMENT_IN_PROGRESS·REQUESTED 로 남겨 스케줄러 보정에 맡긴다.")
    @Test
    void keepsInProgress_whenPgIndeterminate() {
        // arrange — 재고 10, PENDING 견적
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 2);
        fakePgGateway.requestBehavior = () -> {
            throw new PgIndeterminateException("타임아웃", null);
        };

        // act — 예외를 삼키고 transactionKey 없는 응답을 돌려준다
        PaymentRequestInfo result = paymentApplicationService.requestPayment(
            1L, pending.getId(), CardType.SAMSUNG, "1234-5678-1234-5678", pending.getTotalPrice());

        // assert — 미확정이므로 실패로 단정하지 않음. 자원도 복구하지 않음(점유 유지)
        assertThat(result.transactionKey()).isNull();
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(paymentRepository.findByOrderId(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.REQUESTED);
        assertThat(stockOf(product)).isEqualTo(8);
    }
}
