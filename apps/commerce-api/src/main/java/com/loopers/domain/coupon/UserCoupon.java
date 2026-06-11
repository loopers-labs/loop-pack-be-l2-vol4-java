package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 내 쿠폰 — 사용자가 템플릿으로 발급받은 쿠폰 한 장.
 * 발급 시점에 템플릿의 할인 정책·이름·만료일을 복사(스냅샷)해 자립하므로,
 * {@code templateId} 는 ID 참조일 뿐 이후 템플릿 변경의 영향을 받지 않는다.
 * 사용 사실(어느 주문에서 썼는지)은 {@code orderId} 로 단방향 보관한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_coupons_user_template", columnNames = {"user_id", "template_id"})
)
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "coupon_name", nullable = false, length = 100)
    private String couponName;

    @Embedded
    private DiscountPolicy discountPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "order_id")
    private Long orderId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private UserCoupon(Long userId, Long templateId, String couponName,
                       DiscountPolicy discountPolicy, CouponStatus status, ZonedDateTime expiresAt) {
        validateUserId(userId);
        validateTemplateId(templateId);
        validateCouponName(couponName);
        validateDiscountPolicy(discountPolicy);
        validateStatus(status);
        validateExpiresAt(expiresAt);
        this.userId = userId;
        this.templateId = templateId;
        this.couponName = couponName;
        this.discountPolicy = discountPolicy;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    /**
     * 템플릿으로 새 쿠폰을 발급한다 — 할인 정책·이름을 복사하고 만료일(발급시각 + 유효일수)을 확정한다.
     */
    public static UserCoupon issue(Long userId, CouponTemplate template, ZonedDateTime issuedAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 대상 사용자는 비어있을 수 없습니다.");
        }
        if (template == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급할 쿠폰 템플릿은 비어있을 수 없습니다.");
        }
        DiscountPolicy snapshot = DiscountPolicy.of(
                template.getDiscountPolicy().getType(),
                template.getDiscountPolicy().getValue(),
                template.getDiscountPolicy().getMinOrderAmount());
        return new UserCoupon(
                userId,
                template.getId(),
                template.getName(),
                snapshot,
                CouponStatus.AVAILABLE,
                template.issueExpiresAt(issuedAt));
    }

    /**
     * 적용 전 금액에 이 쿠폰의 할인 정책을 적용한 할인액을 계산한다(상태와 무관한 순수 계산).
     */
    public Money calculateDiscount(Money orderAmount) {
        return discountPolicy.calculate(orderAmount);
    }

    /**
     * 쿠폰을 사용 처리한다 — 사용 가능(미사용·미만료)일 때만 USED 로 전이하고 사용 주문·시각을 기록한다.
     * 이미 사용됐거나 만료됐으면 거부한다(재사용·만료 사용 방지).
     */
    public void use(Long orderId, ZonedDateTime now) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 주문은 비어있을 수 없습니다.");
        }
        if (status == CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = now;
        this.orderId = orderId;
    }

    /**
     * 조회 시점 기준 노출 상태. USED 는 그대로, AVAILABLE 이면서 만료일이 지났으면 EXPIRED 로 파생한다.
     */
    public CouponStatus displayStatus(ZonedDateTime now) {
        if (status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        return isExpired(now) ? CouponStatus.EXPIRED : CouponStatus.AVAILABLE;
    }

    public boolean isUsable(ZonedDateTime now) {
        return status == CouponStatus.AVAILABLE && !isExpired(now);
    }

    public boolean isOwnedBy(Long userId) {
        if (userId == null) {
            return false;
        }
        return this.userId.equals(userId);
    }

    /**
     * 사용 가능 여부를 검증한다 — 본인 소유가 아니면 FORBIDDEN, 미사용·미만료가 아니면 BAD_REQUEST.
     * 주문 흐름은 재고 비관락을 잡기 '전에' 이걸 먼저 호출해, 무효 쿠폰이 핫 로우 락을 점유하지 않게 한다(fail-cheap-first).
     */
    public void assertUsableBy(Long userId, ZonedDateTime now) {
        if (!isOwnedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 소유의 쿠폰이 아닙니다.");
        }
        if (!isUsable(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
    }

    private boolean isExpired(ZonedDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기준 시각은 비어있을 수 없습니다.");
        }
        return now.isAfter(expiresAt);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "보유자는 비어있을 수 없습니다.");
        }
    }

    private void validateTemplateId(Long templateId) {
        if (templateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 원형 템플릿은 비어있을 수 없습니다.");
        }
    }

    private void validateCouponName(String couponName) {
        if (couponName == null || couponName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명 스냅샷은 비어있을 수 없습니다.");
        }
    }

    private void validateDiscountPolicy(DiscountPolicy discountPolicy) {
        if (discountPolicy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 정책 스냅샷은 비어있을 수 없습니다.");
        }
    }

    private void validateStatus(CouponStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 상태는 비어있을 수 없습니다.");
        }
    }

    private void validateExpiresAt(ZonedDateTime expiresAt) {
        if (expiresAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 비어있을 수 없습니다.");
        }
    }
}
