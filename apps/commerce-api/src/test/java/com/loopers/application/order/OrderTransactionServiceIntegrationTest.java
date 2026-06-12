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
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 무점유 주문 + 승인 직전 점유(bind) 트랜잭션 단위 통합 테스트.
 *
 * <p>TX1(무점유 견적) / TX2a(자원 점유) / TX2b(확정·보상)의 DB 상태 변화를 직접 검증한다.
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

    private int stockOf(ProductModel product) {
        return stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity();
    }

    @DisplayName("TX1(견적): 주문이 PENDING 으로 저장되고 재고/쿠폰은 점유되지 않는다.")
    @Test
    void createPendingOrder_holdsNothing() {
        // arrange — 쿠폰 포함 견적
        ProductModel product = givenProductWithStock(10);
        CouponModel coupon = couponRepository.save(
            new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, ZonedDateTime.now().plusDays(1)));
        UserCouponModel userCoupon = userCouponRepository.save(UserCouponModel.issue(1L, coupon));

        // act
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), userCoupon.getId());

        // assert — 무점유: 재고 그대로, 쿠폰 AVAILABLE 유지. 할인/연결은 견적에 반영됨
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(userCouponRepository.findById(userCoupon.getId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertThat(pending.getUserCouponId()).isEqualTo(userCoupon.getId());
        assertThat(pending.getTotalPrice()).isEqualTo(140_000L);   // 150,000 - 10,000
    }

    @DisplayName("TX1(견적): 재고가 부족하면 BAD_REQUEST — 품절 상품으로 결제창까지 가는 것을 막는다.")
    @Test
    void createPendingOrder_rejectsWhenStockInsufficient() {
        ProductModel product = givenProductWithStock(2);

        CoreException result = assertThrows(CoreException.class, () ->
            orderTransactionService.createPendingOrder(
                1L, List.of(new OrderItemCommand(product.getId(), 3)), null));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("TX1(견적): 삭제된 상품은 주문할 수 없다.")
    @Test
    void createPendingOrder_rejectsDeletedProduct() {
        // arrange
        ProductModel product = givenProductWithStock(10);
        product.delete();
        productRepository.save(product);

        // act
        CoreException result = assertThrows(CoreException.class, () ->
            orderTransactionService.createPendingOrder(
                1L, List.of(new OrderItemCommand(product.getId(), 1)), null));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("TX1(견적): 이미 사용된 쿠폰은 견적 단계에서 차단된다 — 결제창까지 가기 전에 거부.")
    @Test
    void createPendingOrder_rejectsUsedCoupon() {
        // arrange — 쿠폰을 사용 처리된 상태로 만든다
        ProductModel product = givenProductWithStock(10);
        CouponModel coupon = couponRepository.save(
            new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, ZonedDateTime.now().plusDays(1)));
        UserCouponModel userCoupon = UserCouponModel.issue(1L, coupon);
        userCoupon.use(ZonedDateTime.now());
        userCoupon = userCouponRepository.save(userCoupon);
        Long usedCouponId = userCoupon.getId();

        // act
        CoreException result = assertThrows(CoreException.class, () ->
            orderTransactionService.createPendingOrder(
                1L, List.of(new OrderItemCommand(product.getId(), 1)), usedCouponId));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(result.getMessage()).contains("이미 사용된 쿠폰");
    }

    @DisplayName("TX2a(점유): 재고가 원자 차감되고, 쿠폰이 USED 가 되고, 주문은 PAYMENT_IN_PROGRESS 가 된다.")
    @Test
    void bindResources_deductsStockAndUsesCoupon() {
        // arrange
        ProductModel product = givenProductWithStock(10);
        CouponModel coupon = couponRepository.save(
            new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, ZonedDateTime.now().plusDays(1)));
        UserCouponModel userCoupon = userCouponRepository.save(UserCouponModel.issue(1L, coupon));
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), userCoupon.getId());

        // act
        orderTransactionService.bindResources(pending.getId());

        // assert
        OrderModel bound = orderRepository.findById(pending.getId()).orElseThrow();
        assertThat(bound.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(bound.getPaymentStartedAt()).isNotNull();
        assertThat(stockOf(product)).isEqualTo(7);
        assertThat(userCouponRepository.findById(userCoupon.getId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
    }

    @DisplayName("TX2a(점유): 견적 후 다른 주문이 재고를 가져갔으면 차감이 거부되고 아무것도 점유되지 않는다.")
    @Test
    void bindResources_rejectsWhenStockTakenAfterQuote() {
        // arrange — 재고 5: 두 주문 모두 3개씩 견적 성공 (무점유라 둘 다 통과)
        ProductModel product = givenProductWithStock(5);
        OrderModel orderA = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), null);
        OrderModel orderB = orderTransactionService.createPendingOrder(
            2L, List.of(new OrderItemCommand(product.getId(), 3)), null);

        // act — A가 먼저 점유 (5 → 2), B는 조건부 UPDATE 0행으로 거부
        orderTransactionService.bindResources(orderA.getId());
        CoreException result = assertThrows(CoreException.class, () ->
            orderTransactionService.bindResources(orderB.getId()));

        // assert — B의 점유는 전부 롤백: 재고 2 유지, B는 PENDING 그대로
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(stockOf(product)).isEqualTo(2);
        assertThat(orderRepository.findById(orderB.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("TX2b(보상): 점유된 재고/쿠폰이 복구되고 주문은 FAILED 가 된다.")
    @Test
    void releaseAndFail_restoresBoundResources() {
        // arrange — 점유까지 진행 (재고 10 → 7, 쿠폰 USED)
        ProductModel product = givenProductWithStock(10);
        CouponModel coupon = couponRepository.save(
            new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, ZonedDateTime.now().plusDays(1)));
        UserCouponModel userCoupon = userCouponRepository.save(UserCouponModel.issue(1L, coupon));
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), userCoupon.getId());
        orderTransactionService.bindResources(pending.getId());

        // act — 승인 실패 보상
        orderTransactionService.releaseAndFail(pending.getId());

        // assert — 재고 원복 + 쿠폰 원복 + FAILED
        UserCouponModel restoredCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(restoredCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(restoredCoupon.getUsedAt()).isNull();
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
    }

    @DisplayName("TX2b(보상): 점유 전(PENDING) 주문에는 아무것도 복구하지 않는다 — 재고 증식 방지.")
    @Test
    void releaseAndFail_skipsUnboundOrder() {
        // arrange — 견적만 (무점유)
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 3)), null);

        // act
        orderTransactionService.releaseAndFail(pending.getId());

        // assert — 차감한 적 없는 재고가 "복구"되어 10 → 13 이 되면 안 된다
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("TX2b(확정): 점유 완료 주문이 COMPLETED 로 확정된다.")
    @Test
    void completePayment_marksOrderCompleted() {
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 1)), null);
        orderTransactionService.bindResources(pending.getId());

        OrderInfo completed = orderTransactionService.completePayment(pending.getId());

        assertThat(completed.status()).isEqualTo(OrderStatus.COMPLETED.name());
    }

    @DisplayName("견적 폐기: PENDING 주문은 FAILED 로 닫히고, 이미 점유된 주문은 건드리지 않는다.")
    @Test
    void markOrderFailed_closesOnlyPendingOrder() {
        // arrange — PENDING 주문과 점유 완료 주문
        ProductModel product = givenProductWithStock(10);
        OrderModel pendingOrder = orderTransactionService.createPendingOrder(
            1L, List.of(new OrderItemCommand(product.getId(), 1)), null);
        OrderModel boundOrder = orderTransactionService.createPendingOrder(
            2L, List.of(new OrderItemCommand(product.getId(), 1)), null);
        orderTransactionService.bindResources(boundOrder.getId());

        // act
        orderTransactionService.markOrderFailed(pendingOrder.getId());
        orderTransactionService.markOrderFailed(boundOrder.getId());   // no-op 이어야 함

        // assert
        assertThat(orderRepository.findById(pendingOrder.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
        assertThat(orderRepository.findById(boundOrder.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }
}
