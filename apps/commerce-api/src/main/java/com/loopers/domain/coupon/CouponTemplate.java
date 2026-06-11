package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.domain.common.MoneyConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "coupon_template")
public class CouponTemplate extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    /** type이 해석하는 원시 값 — FIXED는 원, RATE는 퍼센트. 유효 범위 검증은 {@link CouponType#validateValue(long)}에 위임한다. */
    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    /** 최소 주문 금액 조건. 없으면 null. 순수 금액이라 Money VO로 표현한다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "min_order_amount")
    private Money minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponTemplate() {}

    public CouponTemplate(String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        apply(name, type, discountValue, minOrderAmount, expiredAt);
    }

    /** 템플릿 내용을 갱신한다(어드민 수정). 생성과 동일한 불변식을 적용한다. */
    public void update(String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        apply(name, type, discountValue, minOrderAmount, expiredAt);
    }

    private void apply(String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
        type.validateValue(discountValue);
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일시는 비어있을 수 없습니다.");
        }
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount == null ? null : Money.of(minOrderAmount);
        this.expiredAt = expiredAt;
    }

    /** 최소 주문 금액 조건을 만족하는지. 조건이 없으면(null) 항상 만족한다. */
    public boolean satisfiesMinOrderAmount(Money orderAmount) {
        return minOrderAmount == null || orderAmount.value() >= minOrderAmount.value();
    }

    public boolean isExpired(ZonedDateTime at) {
        return !at.isBefore(expiredAt);
    }

    /** 할인액을 계산한다. 최소 주문 금액·만료 검증은 호출 측 책임이다. */
    public Money calculateDiscount(Money orderAmount) {
        return type.discount(orderAmount, discountValue);
    }
}
