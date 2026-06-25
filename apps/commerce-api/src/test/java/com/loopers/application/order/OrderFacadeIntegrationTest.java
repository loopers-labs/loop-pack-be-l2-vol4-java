package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 주문 실행 통합 — 결제 분리(03 §3.7) 이후 placeOrder는 PENDING 주문만 생성한다(재고·쿠폰 차감 포함).
 * 주문 확정(markPaid/markFailed)은 결제 콜백/Reconcile 경로의 책임이며, 여기서는 그 확정의
 * 재고 보존/원복 보상만 직접(orderService) 호출해 검증한다.
 */
@SpringBootTest
public class OrderFacadeIntegrationTest {

    @Autowired OrderFacade orderFacade;
    @Autowired OrderService orderService;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired StockService stockService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
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

    @Nested
    @DisplayName("주문을 실행할 때")
    class PlaceOrder {

        @DisplayName("주문은 PENDING으로 생성되고 재고가 차감된다.")
        @Test
        void given_order_when_placeOrder_then_pendingAndStockDeducted() {
            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("PENDING"),
                    () -> assertThat(info.totalAmount()).isEqualTo(20000L),
                    () -> assertThat(info.items()).hasSize(1),
                    () -> assertThat(stock()).isEqualTo(8)
            );
        }
    }

    @Nested
    @DisplayName("PENDING 주문을 확정할 때")
    class Finalize {

        @DisplayName("결제 성공(markPaid)이면 주문이 PAID가 되고 재고는 차감된 채 유지된다.")
        @Test
        void given_pendingOrder_when_markPaid_then_paidAndStockKept() {
            OrderInfo placed = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            orderService.markPaid(placed.id());

            assertAll(
                    () -> assertThat(orderFacade.getOrder(placed.id()).status()).isEqualTo("PAID"),
                    () -> assertThat(stock()).isEqualTo(8)
            );
        }

        @DisplayName("결제 실패(markFailed)면 주문이 FAILED가 되고 재고가 원복된다.")
        @Test
        void given_pendingOrder_when_markFailed_then_failedAndStockRestored() {
            OrderInfo placed = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            orderService.markFailed(placed.id(), "결제 거절");

            OrderInfo finalized = orderFacade.getOrder(placed.id());
            assertAll(
                    () -> assertThat(finalized.status()).isEqualTo("FAILED"),
                    () -> assertThat(finalized.failureReason()).isNotBlank(),
                    () -> assertThat(stock()).isEqualTo(10)   // 원복
            );
        }
    }
}
