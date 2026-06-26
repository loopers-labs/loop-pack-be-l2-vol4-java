package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 쿠폰 템플릿(Admin이 관리). 발급되면 {@link UserCoupon} 인스턴스가 생긴다.
 * 비즈니스 규칙: 정액/정률 두 종류, 최소 주문 금액 제약(선택), 만료 시각 보유.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    private String name;

    @Enumerated(EnumType.STRING)
    private CouponType type;

    /** 정액: 원 단위 / 정률: 퍼센트 (1~100) */
    private Long value;

    /** 최소 주문 금액. null 이면 제약 없음. */
    private Long minOrderAmount;

    private LocalDateTime expiredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Coupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 종류는 필수입니다.");
        }
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 100% 이하여야 합니다.");
        }
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static Coupon create(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return Coupon.builder()
            .name(name).type(type).value(value).minOrderAmount(minOrderAmount).expiredAt(expiredAt)
            .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiredAt);
    }

    /**
     * 주어진 주문 총액에 대해 할인 금액을 계산한다.
     * - 최소 주문 금액 미달이면 BAD_REQUEST
     * - 할인 결과가 주문 총액을 넘으면 주문 총액만큼만 할인 (음수 결제 방지)
     * - 정률은 소수점 버림(원 단위)
     */
    public Money discount(Money orderTotal) {
        BigDecimal total = orderTotal.amount();
        if (minOrderAmount != null && total.compareTo(BigDecimal.valueOf(minOrderAmount)) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount + "원)을 충족하지 않습니다.");
        }

        BigDecimal raw = switch (type) {
            case FIXED -> BigDecimal.valueOf(value);
            case RATE -> total.multiply(BigDecimal.valueOf(value))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        };

        BigDecimal capped = raw.compareTo(total) > 0 ? total : raw;
        return Money.of(capped);
    }
}
