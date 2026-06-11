package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 트랜잭션 분리(TX1/TX2) 통합 테스트.
 *
 * <p>FakePaymentGateway 가 항상 성공을 반환하므로 결제 실패 보상 경로는
 * 오케스트레이터 경유로는 재현할 수 없다. 여기서는 {@link OrderTransactionService}를
 * 직접 호출해 TX1(PENDING 생성)과 TX2(보상/확정)의 DB 상태 변화를 검증한다.
 */
@SpringBootTest
class OrderTransactionServiceIntegrationTest {

    @Autowired private OrderTransactionService orderTransactionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

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

    @DisplayName("TX1: 주문이 PENDING 으로 저장되고 재고가 차감된 상태로 커밋된다.")
    @Test
    void createPendingOrder_commitsPendingOrderWithDeductedStock() {
        // arrange
        ProductModel product = givenProductWithStock(10);

        // act — TX1 만 수행 (결제 전 상태)
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), null);

        // assert — PENDING + 재고 차감이 이미 커밋되어 있음
        StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @DisplayName("TX2(실패 보상): 재고가 복구되고, 쿠폰이 AVAILABLE 로 돌아오고, 주문은 FAILED 가 된다.")
    @Test
    void failPaymentAndRelease_restoresStockAndCoupon_andFailsOrder() {
        // arrange — 쿠폰 적용 주문을 PENDING 까지 진행 (재고 10 → 7, 쿠폰 USED)
        ProductModel product = givenProductWithStock(10);
        CouponModel coupon = couponRepository.save(
            new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, ZonedDateTime.now().plusDays(1)));
        UserCouponModel userCoupon = userCouponRepository.save(UserCouponModel.issue(1L, coupon));

        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), userCoupon.getId());
        assertThat(userCouponRepository.findById(userCoupon.getId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        // 사용된 쿠폰이 주문에 영속화됨 — 보상 요청(별도 HTTP 요청)에서 복구 대상 식별의 근거
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getUserCouponId())
            .isEqualTo(userCoupon.getId());

        // act — 결제 실패 보상
        orderTransactionService.failPaymentAndRelease(pending.getId());

        // assert — 재고 원복 + 쿠폰 원복 + 주문 FAILED
        StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        UserCouponModel restoredCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
        OrderModel failedOrder = orderRepository.findById(pending.getId()).orElseThrow();

        assertThat(stock.getQuantity()).isEqualTo(10);
        assertThat(restoredCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(restoredCoupon.getUsedAt()).isNull();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @DisplayName("TX2(성공): 주문이 COMPLETED 로 확정된다.")
    @Test
    void completePayment_marksOrderCompleted() {
        // arrange
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 1)), null);

        // act
        OrderInfo completed = orderTransactionService.completePayment(pending.getId());

        // assert
        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED.name());
    }
}
