package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.payment.FakePaymentGateway;
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

@SpringBootTest
public class OrderFacadeIntegrationTest {

    @Autowired OrderFacade orderFacade;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired FakePaymentGateway fakePaymentGateway;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        fakePaymentGateway.reset();   // 기본 SUCCESS로 복원
        BrandModel brand = brandService.register("나이키", "스포츠");
        ProductModel product = productService.createProduct(brand.getId(), "에어맥스", "러닝화", null, 10000L, 10);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private int stock() {
        return productService.getProduct(productId).getStock();
    }

    @Nested
    @DisplayName("주문을 실행할 때")
    class PlaceOrder {

        @DisplayName("PG가 성공하면 주문이 PAID가 되고 재고가 차감된 상태로 유지된다.")
        @Test
        void given_pgSuccess_when_placeOrder_then_paid() {
            fakePaymentGateway.setForcedStatus(PgStatus.SUCCESS);

            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("PAID"),
                    () -> assertThat(info.totalAmount()).isEqualTo(20000L),
                    () -> assertThat(info.items()).hasSize(1),
                    () -> assertThat(stock()).isEqualTo(8)
            );
        }

        @DisplayName("PG가 실패하면 주문이 FAILED가 되고 재고가 원복된다.")
        @Test
        void given_pgFailed_when_placeOrder_then_failedAndStockRestored() {
            fakePaymentGateway.setForcedStatus(PgStatus.FAILED);

            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("FAILED"),
                    () -> assertThat(info.failureReason()).isNotBlank(),
                    () -> assertThat(stock()).isEqualTo(10)   // 원복
            );
        }

        @DisplayName("PG가 타임아웃이면 주문은 PENDING으로 유지되고 재고는 차감된 채 남는다.")
        @Test
        void given_pgTimeout_when_placeOrder_then_pending() {
            fakePaymentGateway.setForcedStatus(PgStatus.TIMEOUT);

            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)));

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("PENDING"),
                    () -> assertThat(stock()).isEqualTo(8)   // 차감 유지 (reconcile 대상)
            );
        }
    }
}
