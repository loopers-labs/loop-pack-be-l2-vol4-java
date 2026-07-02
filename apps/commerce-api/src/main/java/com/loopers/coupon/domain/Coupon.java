package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 애그리거트 루트.
 *
 * <p>할인 정책(정액/정률, 할인값, 최소 주문 금액, 만료일시)을 보유하고 주문 금액에 대한 할인 금액을 계산한다. 발급된 회원 쿠폰은 {@link
 * MemberCoupon} 으로 표현한다.
 */
@Getter
@Entity
@Table(name = "coupon")
@SQLRestriction("deleted_at is null")
public class Coupon extends BaseEntity {

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "discount_value", nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected Coupon() {}

    public Coupon(
        String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void update(
        String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    /** 주문 금액에 대한 할인 금액을 계산한다. 최소 주문 금액 미달 시 예외. 할인 금액은 주문 금액을 초과하지 않는다. */
    public long calculateDiscount(long orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(
                ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount + ")을 충족하지 않습니다. (주문 금액: " + orderAmount + ")");
        }
        long raw =
            switch (type) {
                case FIXED -> value;
                case RATE -> orderAmount * value / 100; // 정수 내림
            };
        return Math.min(raw, orderAmount); // 결제 금액이 음수가 되지 않도록 캡
    }

    public boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    private static void validate(
        String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 필수입니다.");
        }
        switch (type) {
            case FIXED -> {
                if (value <= 0) {
                    throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 1 이상이어야 합니다.");
                }
            }
            case RATE -> {
                if (value <= 0 || value > 100) {
                    throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 1~100 사이여야 합니다.");
                }
            }
        }
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일시는 필수입니다.");
        }
    }
}
