package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    private static final long RATE_MAX = 100L;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    private Coupon(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        assign(name, type, value, minOrderAmount, expiredAt);
    }

    public static Coupon create(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return new Coupon(name, type, value, minOrderAmount, expiredAt);
    }

    public void update(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        assign(name, type, value, minOrderAmount, expiredAt);
    }

    public UserCoupon issueTo(Long userId) {
        return UserCoupon.issue(getId(), userId, type, value, minOrderAmount, expiredAt);
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    private void assign(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private static void validate(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료 시각은 비어있을 수 없습니다.");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, CouponErrorCode.INVALID_COUPON_VALUE);
        }
        if (type == CouponType.RATE && value > RATE_MAX) {
            throw new CoreException(ErrorType.BAD_REQUEST, CouponErrorCode.INVALID_COUPON_VALUE);
        }
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, CouponErrorCode.INVALID_COUPON_VALUE);
        }
    }
}
