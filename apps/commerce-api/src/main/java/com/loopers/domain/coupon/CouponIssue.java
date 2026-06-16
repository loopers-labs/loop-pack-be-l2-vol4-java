package com.loopers.domain.coupon;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "coupon_issues",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_coupon_issue_user_template",
            columnNames = {"user_id", "coupon_template_id"}
        )
    }
)
public class CouponIssue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "coupon_name", nullable = false)
    private String couponName;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType couponType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount_amount")
    private BigDecimal maxDiscountAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Version
    private Long version;

    public CouponIssue(Long userId, CouponTemplate template) {
        this.userId = userId;
        this.couponTemplateId = template.getId();
        this.couponName = template.getName();
        this.couponType = template.getType();
        this.discountValue = template.getValue();
        this.minOrderAmount = template.getMinOrderAmount();
        this.maxDiscountAmount = template.getMaxDiscountAmount();
        this.expiredAt = template.getExpiredAt();
        this.status = CouponStatus.AVAILABLE;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiredAt);
    }

    public BigDecimal use(BigDecimal orderAmount, LocalDateTime now) {
        // 1. 이미 사용된 쿠폰인지 검증
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 완료된 쿠폰입니다.");
        }

        // 2. 만료 여부 검증
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        // 3. 최소 주문 금액 검증
        if (orderAmount.compareTo(this.minOrderAmount) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 못했습니다.");
        }

        // 4. 할인 금액 계산
        BigDecimal discount = BigDecimal.ZERO;
        if (this.couponType == CouponType.FIXED) {
            discount = this.discountValue;
            if (discount.compareTo(orderAmount) > 0) {
                discount = orderAmount;
            }
        } else if (this.couponType == CouponType.RATE) {
            // 정률 계산: orderAmount * (value / 100)
            BigDecimal rate = this.discountValue.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            discount = orderAmount.multiply(rate).setScale(0, RoundingMode.HALF_UP);

            // 최대 할인 금액 한도 적용
            if (this.maxDiscountAmount != null && discount.compareTo(this.maxDiscountAmount) > 0) {
                discount = this.maxDiscountAmount;
            }
        }

        // 5. 사용 상태로 전환
        this.status = CouponStatus.USED;

        return discount;
    }
}
