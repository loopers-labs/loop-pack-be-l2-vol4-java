package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 쿠폰 정책(템플릿). 관리자가 관리하며, 발급(issueTo)의 유일한 진입점.
 * soft delete = 신규 발급 중단 (기발급 쿠폰은 스냅샷으로 영향 없음).
 */
@Entity
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "discount_value", nullable = false)
    private long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected Coupon() {}

    public Coupon(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private static void validate(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        new CouponSnapshot(name, type, value, minOrderAmount); // 정책 필드 검증 위임 (스냅샷과 동일 규칙)
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }

    public UserCoupon issueTo(Long userId, ZonedDateTime now) {
        if (getDeletedAt() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 쿠폰은 발급할 수 없습니다.");
        }
        if (!expiredAt.isAfter(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return new UserCoupon(userId, getId(), snapshot(), now, expiredAt);
    }

    public CouponSnapshot snapshot() {
        return new CouponSnapshot(name, type, value, minOrderAmount);
    }

    public void update(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }
}
