package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 — 어드민이 정의하는 쿠폰의 원형.
 * 발급 시 {@link UserCoupon} 이 이 템플릿의 할인 정책·이름·만료일을 복사(스냅샷)하므로,
 * 이후 템플릿이 수정·삭제돼도 이미 발급된 쿠폰의 가치는 변하지 않는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupon_templates")
public class CouponTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Embedded
    private DiscountPolicy discountPolicy;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    private CouponTemplate(String name, DiscountPolicy discountPolicy, int validDays) {
        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateValidDays(validDays);
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.validDays = validDays;
    }

    public static CouponTemplate create(String name, DiscountPolicy discountPolicy, int validDays) {
        return new CouponTemplate(name, discountPolicy, validDays);
    }

    public void modify(String name, DiscountPolicy discountPolicy, int validDays) {
        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateValidDays(validDays);
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.validDays = validDays;
    }

    /**
     * 발급 시각 기준으로 발급될 쿠폰의 만료 시각을 계산한다(발급일 + 유효일수).
     */
    public ZonedDateTime issueExpiresAt(ZonedDateTime issuedAt) {
        if (issuedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 시각은 비어있을 수 없습니다.");
        }
        return issuedAt.plusDays(validDays);
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
    }

    private void validateDiscountPolicy(DiscountPolicy discountPolicy) {
        if (discountPolicy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 정책은 비어있을 수 없습니다.");
        }
    }

    private void validateValidDays(int validDays) {
        if (validDays < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효일수는 1 이상이어야 합니다.");
        }
    }
}
