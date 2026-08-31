package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

// Hides: discount boundary validation, immutable snapshots, and retry idempotency.
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    private Long buyerId;
    private Long originalAmount;
    private Long discountAmount;
    private Long finalAmount;
    private Long appliedCouponId;
    private Instant discountAppliedAt;
    private boolean confirmed;

    protected Order() {}

    public Order(long buyerId, long originalAmount) {
        if (buyerId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "buyerId must be positive");
        }
        if (originalAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "originalAmount must not be negative");
        }
        this.buyerId = buyerId;
        this.originalAmount = originalAmount;
        this.discountAmount = 0L;
        this.finalAmount = originalAmount;
    }

    public void applyDiscount(long couponId, long discountAmount, Instant requestStartedAt) {
        if (appliedCouponId != null) {
            if (appliedCouponId == couponId) {
                return;
            }
            throw new CoreException(ErrorType.BAD_REQUEST, "only one coupon may be applied");
        }
        if (confirmed) {
            throw new CoreException(ErrorType.BAD_REQUEST, "a confirmed order cannot be discounted");
        }
        if (couponId <= 0 || requestStartedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "coupon and request start are required");
        }
        if (discountAmount < 0 || discountAmount > originalAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "discount must be within the original amount");
        }
        this.appliedCouponId = couponId;
        this.discountAmount = discountAmount;
        this.finalAmount = originalAmount - discountAmount;
        this.discountAppliedAt = requestStartedAt;
    }

    public void confirm() {
        confirmed = true;
    }

    public void requireOwner(long candidateBuyerId) {
        if (buyerId != candidateBuyerId) throw new CoreException(ErrorType.BAD_REQUEST, "order owner mismatch");
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public Long getOriginalAmount() {
        return originalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return finalAmount;
    }

    public Long getAppliedCouponId() {
        return appliedCouponId;
    }

    public Instant getDiscountAppliedAt() {
        return discountAppliedAt;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
