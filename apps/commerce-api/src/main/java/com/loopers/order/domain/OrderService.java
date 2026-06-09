package com.loopers.order.domain;

import com.loopers.coupon.domain.CouponModel;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
public class OrderService {

    public OrderModel getOrThrow(Optional<OrderModel> order) {
        return order.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));
    }

    public void checkOwnership(OrderModel order, Long userId) {
        if (!new OrderOwnerSpecification(userId).isSatisfiedBy(order)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
    }

    public OrderModel createOrder(Long userId, List<ProductModel> products, Map<Long, Integer> quantities) {
        return createOrder(userId, products, quantities, null, null);
    }

    public OrderModel createOrder(Long userId, List<ProductModel> products, Map<Long, Integer> quantities, CouponModel coupon, Long couponIssueId) {
        List<OrderItemModel> items = products.stream()
            .map(p -> {
                int qty = quantities.getOrDefault(p.getId(), 0);
                return new OrderItemModel(p.getId(), p.getName(), p.getPrice(), qty);
            })
            .toList();
        long originalAmount = items.stream().mapToLong(i -> i.getPrice() * i.getQuantity()).sum();
        long discountAmount = coupon != null ? coupon.calculateDiscount(originalAmount) : 0L;
        return new OrderModel(userId, items, couponIssueId, discountAmount);
    }
}
