package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.infrastructure.payment.FakePaymentGateway;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * PENDING 주문 reconcile — pay()가 TIMEOUT으로 끝나 PENDING으로 남은 주문을
 * PG 재조회(inquire)로 최종 확정하는 흐름을 검증한다 (01 §7.6 보상 트랜잭션의 마지막 조각).
 *
 * - inquire=SUCCESS → PAID, 재고·쿠폰 차감 유지
 * - inquire=FAILED → FAILED, 재고·쿠폰 원복
 * - inquire=TIMEOUT → 아직 미확정이라 PENDING 유지
 */
@SpringBootTest
public class OrderReconcileIntegrationTest {

    @Autowired OrderFacade orderFacade;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired StockService stockService;
    @Autowired FakePaymentGateway fakePaymentGateway;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        fakePaymentGateway.reset();
        BrandModel brand = brandService.register("나이키", "스포츠");
        ProductModel product = productService.createProduct(brand.getId(), "에어맥스", "러닝화", null, 10000L);
        productId = product.getId();
        stockService.initialize(productId, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private int stock() {
        return stockService.getQuantity(productId);
    }

    /** PG 타임아웃으로 PENDING에 남은 주문 1건을 만든다(재고 10 → 8). */
    private Long placeTimedOutOrder() {
        fakePaymentGateway.setForcedStatus(PgStatus.TIMEOUT);
        OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                List.of(new OrderLine(productId, 2)));
        assertThat(info.status()).isEqualTo("PENDING");
        return info.id();
    }

    @DisplayName("PG 재조회 결과가 성공이면 PENDING 주문이 PAID로 확정되고 재고는 차감된 채 유지된다")
    @Test
    void given_pendingOrder_when_reconcileWithSuccess_then_paid() {
        Long orderId = placeTimedOutOrder();
        fakePaymentGateway.setForcedInquiryStatus(PgStatus.SUCCESS);

        ReconcileResult result = orderFacade.reconcilePending(0, 100);

        assertAll(
                () -> assertThat(result.scanned()).isEqualTo(1),
                () -> assertThat(result.paid()).isEqualTo(1),
                () -> assertThat(orderFacade.getOrder(orderId).status()).isEqualTo("PAID"),
                () -> assertThat(stock()).isEqualTo(8)   // 차감 유지
        );
    }

    @DisplayName("PG 재조회 결과가 실패면 PENDING 주문이 FAILED로 확정되고 재고가 원복된다")
    @Test
    void given_pendingOrder_when_reconcileWithFailed_then_failedAndStockRestored() {
        Long orderId = placeTimedOutOrder();
        fakePaymentGateway.setForcedInquiryStatus(PgStatus.FAILED);

        ReconcileResult result = orderFacade.reconcilePending(0, 100);

        assertAll(
                () -> assertThat(result.scanned()).isEqualTo(1),
                () -> assertThat(result.failed()).isEqualTo(1),
                () -> assertThat(orderFacade.getOrder(orderId).status()).isEqualTo("FAILED"),
                () -> assertThat(stock()).isEqualTo(10)   // 원복
        );
    }

    @DisplayName("PG가 여전히 미확정(타임아웃)이면 주문은 PENDING으로 남고 다음 회차로 미뤄진다")
    @Test
    void given_pendingOrder_when_reconcileStillTimeout_then_remainsPending() {
        Long orderId = placeTimedOutOrder();
        fakePaymentGateway.setForcedInquiryStatus(PgStatus.TIMEOUT);

        ReconcileResult result = orderFacade.reconcilePending(0, 100);

        assertAll(
                () -> assertThat(result.scanned()).isEqualTo(1),
                () -> assertThat(result.stillPending()).isEqualTo(1),
                () -> assertThat(orderFacade.getOrder(orderId).status()).isEqualTo("PENDING"),
                () -> assertThat(stock()).isEqualTo(8)   // 차감 유지
        );
    }
}
