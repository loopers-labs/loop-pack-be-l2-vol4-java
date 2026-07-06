package com.loopers.application.payment;

import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgGateway;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pg-simulator 대사 스케줄러({@link PgReconcileScheduler}) 통합 테스트.
 *
 * <p>콜백을 못 받아 PAYMENT_IN_PROGRESS 로 남은 주문을, PG 조회 결과와 만료 데드라인에 따라
 * 어떻게 종결하는지 실제 DB 흐름 위에서 검증한다:
 * <ul>
 *   <li>SUCCESS 발견 → 주문 완료(콜백 유실 복구)</li>
 *   <li>FAILED 발견(PENDING 없음) → 실패 + 자원 복구</li>
 *   <li>미확정 + 만료 전 → 다음 틱 대기(상태 불변)</li>
 *   <li>미확정 + 만료 후 → FAILED + 자원 복구</li>
 * </ul>
 */
@SpringBootTest
@Import(PgReconcileIntegrationTest.FakeGatewayConfig.class)
class PgReconcileIntegrationTest {

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        FakePgGateway fakePgGateway() {
            return new FakePgGateway();
        }
    }

    @Autowired private PgReconcileScheduler pgReconcileScheduler;
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
        fakePgGateway.requestBehavior = () -> "TX-PENDING";
        fakePgGateway.transactions = List.of();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private int stockOf(ProductModel product) {
        return stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity();
    }

    /** 콜백을 못 받아 PAYMENT_IN_PROGRESS·REQUESTED(TID 보존)로 남은 주문을 실제 흐름으로 생성한다. */
    private OrderModel givenInProgressOrder(ProductModel product, int quantity, String transactionKey) {
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), quantity)), null);
        fakePgGateway.requestBehavior = () -> transactionKey;
        paymentApplicationService.requestPayment(
            1L, pending.getId(), CardType.SAMSUNG, "1234-5678-1234-5678", pending.getTotalPrice());
        return orderRepository.findById(pending.getId()).orElseThrow();
    }

    private ProductModel givenProductWithStock(int stockQuantity) {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), stockQuantity));
        return product;
    }

    @DisplayName("PG 조회에 SUCCESS 가 있으면 주문을 완료 처리한다 (콜백 유실 복구).")
    @Test
    void completesOrder_whenPgHasSuccess() {
        // arrange — 점유 완료 후 콜백 미수신 상태
        ProductModel product = givenProductWithStock(10);
        OrderModel order = givenInProgressOrder(product, 2, "TX-1");
        fakePgGateway.transactions = List.of(
            new PgGateway.PgTransactionResult("TX-1", "SUCCESS", null));

        // act
        pgReconcileScheduler.reconcile();

        // assert — 완료 확정, 결제 SUCCESS, 점유 자원 유지
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.COMPLETED);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @DisplayName("PG 조회에 PENDING 없이 FAILED 만 있으면 실패 처리 + 자원을 복구한다.")
    @Test
    void failsAndReleases_whenPgHasFailedOnly() {
        // arrange
        ProductModel product = givenProductWithStock(10);
        OrderModel order = givenInProgressOrder(product, 2, "TX-2");
        fakePgGateway.transactions = List.of(
            new PgGateway.PgTransactionResult("TX-2", "FAILED", "한도초과"));

        // act
        pgReconcileScheduler.reconcile();

        // assert — 실패 확정 + 재고 복구
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @DisplayName("미확정(PENDING)이고 만료 전이면 상태를 바꾸지 않고 다음 틱을 기다린다.")
    @Test
    void waitsNextTick_whenPendingBeforeDeadline() {
        // arrange — 방금 점유 시작(paymentStartedAt ≒ now), PG 는 아직 PENDING
        ProductModel product = givenProductWithStock(10);
        OrderModel order = givenInProgressOrder(product, 2, "TX-3");
        fakePgGateway.transactions = List.of(
            new PgGateway.PgTransactionResult("TX-3", "PENDING", null));

        // act
        pgReconcileScheduler.reconcile();

        // assert — 만료 전이므로 그대로 진행 중. 결제 REQUESTED, 자원 점유 유지
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.REQUESTED);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @DisplayName("FAILED 와 PENDING 이 함께 있고 만료 전이면, PENDING 우선으로 상태를 유지한다(실패 확정 보류).")
    @Test
    void waits_whenFailedAndPendingCoexistBeforeDeadline() {
        // arrange — 같은 주문에 FAILED 와 PENDING 이 섞여 조회됨 (방금 점유, 만료 전)
        ProductModel product = givenProductWithStock(10);
        OrderModel order = givenInProgressOrder(product, 2, "TX-5");
        fakePgGateway.transactions = List.of(
            new PgGateway.PgTransactionResult("TX-5a", "FAILED", "한도초과"),
            new PgGateway.PgTransactionResult("TX-5b", "PENDING", null));

        // act
        pgReconcileScheduler.reconcile();

        // assert — PENDING 이 하나라도 있으면 실패 확정을 미루고 진행 중 유지. 자원도 그대로.
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.REQUESTED);
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @DisplayName("미확정 상태로 만료 시간을 넘기면 더 못 기다리고 FAILED + 자원 복구로 종결한다.")
    @Test
    void failsAndReleases_whenIndeterminateAfterDeadline() {
        // arrange — 점유 시작 시각을 11분 전으로 밀어 만료(10분) 초과 상태로 만든다
        ProductModel product = givenProductWithStock(10);
        OrderModel order = givenInProgressOrder(product, 2, "TX-4");
        ZonedDateTime longAgo = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(11);
        ReflectionTestUtils.setField(order, "paymentStartedAt", longAgo);
        orderRepository.save(order);
        fakePgGateway.transactions = List.of();   // 여전히 결과 미확인

        // act
        pgReconcileScheduler.reconcile();

        // assert — 만료 종결 + 재고 복구
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        assertThat(stockOf(product)).isEqualTo(10);
    }
}
