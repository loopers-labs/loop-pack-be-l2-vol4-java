package com.loopers.order.application;

import com.loopers.coupon.application.CouponUsageService;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.product.domain.ProductErrorCode;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderCompensationService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductStockRepository productStockRepository;
    private final CouponUsageService couponUsageService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(Long orderId) {
        log.info("보상 트랜잭션 시작 orderId={}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "보상 대상 주문을 찾을 수 없습니다."));

        // placeOrder 와 동일하게 productId 오름차순으로 재고 락을 잡아 데드락 여지를 없앤다.
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId).stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();
        for (OrderItem item : items) {
            ProductStock stock = productStockRepository.findByProductIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.STOCK_NOT_FOUND));
            stock.increase(item.getQuantity());
        }
        log.info("재고 복구 완료 orderId={} itemCount={}", orderId, items.size());

        if (order.getUserCouponId() != null) {
            couponUsageService.restore(order.getUserCouponId());
        }

        order.markFailed();
        log.info("보상 완료 orderId={} status=FAILED", orderId);
    }
}
