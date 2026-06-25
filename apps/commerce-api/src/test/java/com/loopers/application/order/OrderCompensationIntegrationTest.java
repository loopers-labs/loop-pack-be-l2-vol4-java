package com.loopers.application.order;

import com.loopers.domain.coupon.model.CouponStatus;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.model.OrderItem;
import com.loopers.domain.order.model.OrderItemStatus;
import com.loopers.domain.order.repository.OrderItemRepository;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderCompensationIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 보상하면, 재고가 복구되고 쿠폰이 복원되며 주문 항목이 취소된다.")
    @Test
    void compensatesOrder() {
        // Arrange: 재고 3개 차감된 상태(10 -> 7), 쿠폰 사용됨, 주문 항목 ORDERED
        Long productId = 100L;
        stockRepository.save(Stock.create(productId, 7));

        IssuedCoupon coupon = IssuedCoupon.create(1L, 200L);
        coupon.use();
        IssuedCoupon savedCoupon = issuedCouponRepository.save(coupon);

        Order order = orderRepository.save(Order.create(1L, 10_000L, 1_000L, savedCoupon.getId()));
        orderItemRepository.saveAll(List.of(OrderItem.create(order.getId(), productId, 3)));

        // Act
        orderApplicationService.compensateOrder(order.getId());

        // Assert
        assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(issuedCouponRepository.findById(savedCoupon.getId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertThat(orderItemRepository.findAllByOrderId(order.getId()).get(0).getStatus())
            .isEqualTo(OrderItemStatus.CANCELLED);
    }

    @DisplayName("이미 보상된 주문을 다시 보상해도, 재고가 중복 복구되지 않는다.(멱등)")
    @Test
    void compensateIsIdempotent() {
        // Arrange
        Long productId = 101L;
        stockRepository.save(Stock.create(productId, 7));
        Order order = orderRepository.save(Order.create(1L, 10_000L, 0L, null));
        orderItemRepository.saveAll(List.of(OrderItem.create(order.getId(), productId, 3)));

        // Act: 두 번 보상
        orderApplicationService.compensateOrder(order.getId());
        orderApplicationService.compensateOrder(order.getId());

        // Assert: 재고는 한 번만 복구되어 10
        assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(10);
    }
}
