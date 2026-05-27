package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class OrderServiceIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired ProductService productService;
    @Autowired BrandService brandService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long brandId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandService.register("나이키", "스포츠");
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createProduct(long price, int stock) {
        ProductModel p = productService.createProduct(brandId, "에어맥스", "러닝화", null, price, stock);
        return p.getId();
    }

    private int stockOf(Long productId) {
        return productService.getProduct(productId).getStock();
    }

    @Nested
    @DisplayName("주문을 생성(PENDING)할 때")
    class PlaceOrderPending {

        @DisplayName("유효한 주문이면 PENDING으로 생성되고, 재고가 차감되며 totalAmount = Σ lineTotal이다.")
        @Test
        void given_validOrder_when_placeOrderPending_then_pendingAndStockDeducted() {
            Long productId = createProduct(10000L, 10);

            OrderModel order = orderService.placeOrderPending(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            assertAll(
                    () -> assertThat(order.getId()).isNotNull(),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.getTotalAmount().getAmount()).isEqualTo(20000L),
                    () -> assertThat(order.getItems()).hasSize(1),
                    () -> assertThat(stockOf(productId)).isEqualTo(8)
            );
        }

        @DisplayName("한 항목이라도 재고가 부족하면 CONFLICT가 발생하고, 차감이 전부 롤백된다.")
        @Test
        void given_insufficientStockOnOneLine_when_placeOrderPending_then_conflictAndRollback() {
            Long productA = createProduct(10000L, 10);
            Long productB = createProduct(5000L, 1);   // 부족 유발

            Throwable thrown = catchThrowable(() -> orderService.placeOrderPending(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productA, 2), new OrderLine(productB, 5))));

            assertAll(
                    () -> assertThat(thrown).isInstanceOf(CoreException.class),
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> assertThat(stockOf(productA)).isEqualTo(10),   // 롤백 — 차감 안 됨
                    () -> assertThat(stockOf(productB)).isEqualTo(1)
            );
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST가 발생한다.")
        @Test
        void given_emptyLines_when_placeOrderPending_then_throwsBadRequest() {
            Throwable thrown = catchThrowable(() ->
                    orderService.placeOrderPending(USER_ID, PaymentMethod.CARD, List.of()));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는(또는 비활성) 상품이 포함되면 NOT_FOUND가 발생한다.")
        @Test
        void given_nonExistingProduct_when_placeOrderPending_then_throwsNotFound() {
            Long valid = createProduct(10000L, 10);

            Throwable thrown = catchThrowable(() -> orderService.placeOrderPending(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(valid, 1), new OrderLine(9999L, 1))));

            assertAll(
                    () -> assertThat(thrown).isInstanceOf(CoreException.class),
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(stockOf(valid)).isEqualTo(10)   // 롤백 — 유효 상품 재고도 차감 안 됨
            );
        }
    }

    @Nested
    @DisplayName("결제 결과를 반영할 때")
    class Finalize {

        @DisplayName("markPaid하면 PAID가 되고 paidAt이 기록된다.")
        @Test
        void given_pendingOrder_when_markPaid_then_paid() {
            Long productId = createProduct(10000L, 10);
            OrderModel order = orderService.placeOrderPending(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            OrderModel paid = orderService.markPaid(order.getId());

            assertAll(
                    () -> assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID),
                    () -> assertThat(paid.getPaidAt()).isNotNull()
            );
        }

        @DisplayName("markFailed하면 FAILED가 되고 차감했던 재고가 원복된다.")
        @Test
        void given_pendingOrder_when_markFailed_then_failedAndStockRestored() {
            Long productId = createProduct(10000L, 10);
            OrderModel order = orderService.placeOrderPending(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));   // stock 10 → 8

            OrderModel failed = orderService.markFailed(order.getId(), "카드 거절");

            assertAll(
                    () -> assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED),
                    () -> assertThat(failed.getFailureReason()).isEqualTo("카드 거절"),
                    () -> assertThat(stockOf(productId)).isEqualTo(10)   // 원복
            );
        }
    }
}
