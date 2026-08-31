package com.loopers.domain.order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
// Hides: ownership, policy evaluation, and same-coupon snapshot reuse.
@Component @RequiredArgsConstructor
public class OrderDiscountService {
    private final CouponPolicy policy;
    public void apply(Order order, long buyerId, long couponId, Instant startedAt) {
        order.requireOwner(buyerId);
        if (order.getAppliedCouponId() != null && order.getAppliedCouponId() == couponId) return;
        order.applyDiscount(couponId, policy.discount(buyerId, couponId, order.getOriginalAmount(), startedAt), startedAt);
    }
}
