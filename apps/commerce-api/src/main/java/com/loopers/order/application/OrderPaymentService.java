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
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 결제 결과에 따른 주문 전이. 결제 도메인(PaymentResultHandler)이 호출하는 주문측 진입점이다.
 * REQUIRES_NEW 를 쓰지 않아(기본 전파) 콜백 핸들러의 트랜잭션에 합류한다 — 확정과 보상을 한 TX 로 묶기 위함.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductStockRepository productStockRepository;
    private final CouponUsageService couponUsageService;

    @Transactional
    public void markPaid(String orderNumber) {
        Order order = loadOrder(orderNumber);
        order.markPaid();
        log.info("주문 결제 완료 orderNumber={} status=PAID", orderNumber);
    }

    @Transactional
    public void compensate(String orderNumber) {
        Order order = loadOrder(orderNumber);

        // 재고 락은 productId 오름차순으로 잡아 동시 보상/주문 간 데드락 여지를 없앤다.
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();
        for (OrderItem item : items) {
            ProductStock stock = productStockRepository.findByProductIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.STOCK_NOT_FOUND));
            stock.increase(item.getQuantity());
        }

        if (order.getUserCouponId() != null) {
            couponUsageService.restore(order.getUserCouponId());
        }

        order.markPaymentFailed();
        log.info("주문 보상 완료 orderNumber={} status=PAYMENT_FAILED itemCount={}", orderNumber, items.size());
    }

    private Order loadOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR,
                        "결제 결과를 반영할 주문을 찾을 수 없습니다. orderNumber=" + orderNumber));
    }
}
