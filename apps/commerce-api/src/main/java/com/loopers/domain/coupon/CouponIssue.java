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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Version
    private Long version;

    public CouponIssue(Long userId, Long couponTemplateId) {
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = CouponStatus.AVAILABLE;
    }

    public BigDecimal use(CouponTemplate template, BigDecimal orderAmount, LocalDateTime now) {
        // 1. 이미 사용된 쿠폰인지 검증
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 완료된 쿠폰입니다.");
        }

        // 2. 만료 여부 검증
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        // 3. 최소 주문 금액 검증
        if (orderAmount.compareTo(template.getMinOrderAmount()) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 못했습니다.");
        }

        // 4. 할인 금액 계산
        BigDecimal discount = BigDecimal.ZERO;
        if (template.getType() == CouponType.FIXED) {
            discount = template.getValue();
            if (discount.compareTo(orderAmount) > 0) {
                discount = orderAmount;
            }
        } else if (template.getType() == CouponType.RATE) {
            // 정률 계산: orderAmount * (value / 100)
            BigDecimal rate = template.getValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            discount = orderAmount.multiply(rate).setScale(0, RoundingMode.HALF_UP);

            // 최대 할인 금액 한도 적용
            if (template.getMaxDiscountAmount() != null && discount.compareTo(template.getMaxDiscountAmount()) > 0) {
                discount = template.getMaxDiscountAmount();
            }
        }

        // 5. 사용 상태로 전환
        this.status = CouponStatus.USED;

        return discount;
    }
}
