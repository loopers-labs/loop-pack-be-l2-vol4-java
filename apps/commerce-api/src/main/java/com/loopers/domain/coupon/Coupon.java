package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 템플릿(정책). 발급의 원본이 된다.
 * 할인 정책(type+value)은 Discount VO 로, 최소 주문 금액은 Money VO 로 검증한다.
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Embedded
    private Discount discount;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "min_order_amount"))
    private Money minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    // 선착순 발급 한도. null 이면 무제한(기존 어드민 쿠폰과의 호환).
    @Column(name = "total_quantity")
    private Long totalQuantity;

    // 기존 행 호환을 위해 DDL 기본값 0 (ddl-auto update 로 컬럼 추가 시 기존 행이 0 으로 채워지도록)
    @Column(name = "issued_quantity", nullable = false, columnDefinition = "bigint not null default 0")
    private Long issuedQuantity;

    public Coupon(String name, Discount discount, Money minOrderAmount, LocalDateTime expiredAt) {
        this(name, discount, minOrderAmount, expiredAt, null);
    }

    public Coupon(String name, Discount discount, Money minOrderAmount, LocalDateTime expiredAt, Long totalQuantity) {
        validate(name, expiredAt);
        this.name = name;
        this.discount = discount;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0L;
    }

    private static void validate(String name, LocalDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 비어있을 수 없습니다.");
        }
    }

    public void update(String name, Discount discount, Money minOrderAmount, LocalDateTime expiredAt) {
        validate(name, expiredAt);
        this.name = name;
        this.discount = discount;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public Money discountFor(Money orderAmount) {
        if (minOrderAmount != null && orderAmount.isLessThan(minOrderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }
        return discount.apply(orderAmount);
    }

    /** 발급 여력이 남아 있는가. totalQuantity 가 null 이면 무제한. */
    public boolean canIssue() {
        if (totalQuantity == null) {
            return true;
        }
        long issued = issuedQuantity == null ? 0 : issuedQuantity;
        return issued < totalQuantity;
    }

    /** 발급 수량을 1 소진한다. 수량 검증을 우회한 호출을 막는 마지막 안전망으로 초과 시 예외를 던진다. */
    public void issueOne() {
        if (!canIssue()) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰 수량이 모두 소진되었습니다.");
        }
        this.issuedQuantity = (issuedQuantity == null ? 0 : issuedQuantity) + 1;
    }
}
