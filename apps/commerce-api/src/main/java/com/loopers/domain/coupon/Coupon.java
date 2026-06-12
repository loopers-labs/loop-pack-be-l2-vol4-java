package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class Coupon {

    private Long id;
    private String name;
    private CouponType type;
    private Long value;
    private Long minOrderAmount;
    private ZonedDateTime expiredAt;
    private boolean deleted;

    public Coupon(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this(null, name, type, value, minOrderAmount, expiredAt, false);
    }

    private Coupon(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        boolean deleted
    ) {
        validateName(name);
        validateType(type);
        validateValue(type, value);
        validateMinOrderAmount(minOrderAmount);
        validateExpiredAt(expiredAt);

        this.id = id;
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount == null ? 0L : minOrderAmount;
        this.expiredAt = expiredAt;
        this.deleted = deleted;
    }

    public static Coupon reconstruct(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        boolean deleted
    ) {
        return new Coupon(id, name, type, value, minOrderAmount, expiredAt, deleted);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void update(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        validateType(type);
        validateValue(type, value);
        validateMinOrderAmount(minOrderAmount);
        validateExpiredAt(expiredAt);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount == null ? 0L : minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired(ZonedDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 시각은 비어있을 수 없습니다.");
        }
        return expiredAt.isBefore(now) || expiredAt.isEqual(now);
    }

    public IssuedCoupon issueTo(String userLoginId, ZonedDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 시각은 비어있을 수 없습니다.");
        }
        if (id == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "저장되지 않은 쿠폰은 발급할 수 없습니다.");
        }
        if (deleted) {
            throw new CoreException(ErrorType.CONFLICT, "삭제된 쿠폰은 발급할 수 없습니다.");
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        return new IssuedCoupon(id, userLoginId, expiredAt);
    }

    public Long calculateDiscount(Long orderAmount) {
        if (orderAmount == null || orderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
        if (orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰 최소 주문 금액을 만족하지 않습니다.");
        }

        Long discount = switch (type) {
            case FIXED -> value;
            case RATE -> orderAmount * value / 100;
        };
        return Math.min(discount, orderAmount);
    }

    public void delete() {
        this.deleted = true;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
    }

    private void validateType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
    }

    private void validateValue(CouponType type, Long value) {
        if (value == null || value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 값은 1 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 값은 100 이하이어야 합니다.");
        }
    }

    private void validateMinOrderAmount(Long minOrderAmount) {
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
    }

    private void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료 일시는 비어있을 수 없습니다.");
        }
    }
}
