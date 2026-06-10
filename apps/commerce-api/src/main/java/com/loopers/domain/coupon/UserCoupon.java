package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.policy.CouponDiscountPolicy;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.domain.coupon.vo.CouponExpiration;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.CouponName;
import com.loopers.domain.coupon.vo.CouponOwner;
import com.loopers.domain.coupon.vo.CouponTemplateId;
import com.loopers.domain.coupon.vo.DiscountValue;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "user_coupon",
    indexes = @Index(name = "idx_user_coupon_status_template", columnList = "status, coupon_template_id"),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_coupon_user_template",
        columnNames = {"user_id", "coupon_template_id"}
    )
)
public class UserCoupon extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private CouponOwner owner;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "coupon_template_id", nullable = false))
    private CouponTemplateId couponTemplateId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "coupon_name", nullable = false))
    private CouponName name;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType type;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "discount_value", nullable = false))
    private DiscountValue discountValue;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "min_order_amount"))
    private CouponMoney minimumOrderAmount;

    @Embedded
    @AttributeOverride(name = "expiredAt", column = @Column(name = "expired_at", nullable = false))
    private CouponExpiration expiration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private UserCoupon(
        Long userId,
        Long couponTemplateId,
        CouponName name,
        CouponType type,
        DiscountValue discountValue,
        CouponMoney minimumOrderAmount,
        CouponExpiration expiration
    ) {
        this.owner = CouponOwner.of(userId);
        this.couponTemplateId = CouponTemplateId.of(couponTemplateId);
        this.name = requireName(name);
        this.type = requireType(type);
        this.discountValue = requireDiscountValue(discountValue);
        this.minimumOrderAmount = minimumOrderAmount;
        this.expiration = requireExpiration(expiration);
        this.status = UserCouponStatus.AVAILABLE;
    }

    public static UserCoupon issue(Long userId, Long couponTemplateId, CouponTemplate couponTemplate) {
        if (couponTemplate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 비어있을 수 없습니다.");
        }
        return new UserCoupon(
            userId,
            couponTemplateId,
            CouponName.of(couponTemplate.getName()),
            couponTemplate.getType(),
            couponTemplate.getDiscountValue(),
            couponTemplate.getMinimumOrderAmount(),
            couponTemplate.getExpiration()
        );
    }

    public Long getUserId() {
        return owner.userId();
    }

    public Long getCouponTemplateId() {
        return couponTemplateId.value();
    }

    public String getName() {
        return name.value();
    }

    public boolean isIssuedTo(Long userId) {
        return owner.isSameUser(userId);
    }

    public boolean isAvailable() {
        return status == UserCouponStatus.AVAILABLE;
    }

    public boolean canBeUsedBy(Long userId) {
        return isIssuedTo(userId) && isAvailable();
    }

    public boolean canApplyTo(CouponMoney orderAmount, ZonedDateTime now) {
        validateOrderAmount(orderAmount);
        return !isExpiredAt(now) && satisfiesMinimumOrderAmount(orderAmount);
    }

    public void checkUsableBy(Long userId) {
        validateOwner(userId);
        validateAvailable();
    }

    public void checkApplicableTo(CouponMoney orderAmount, ZonedDateTime now) {
        validateOrderAmount(orderAmount);
        validateNotExpired(now);
        validateMinimumOrderAmount(orderAmount);
    }

    public CouponDiscount apply(CouponMoney orderAmount, ZonedDateTime now, CouponDiscountPolicy policy) {
        validatePolicy(policy);
        checkApplicableTo(orderAmount, now);

        CouponMoney discountAmount = policy.discount(orderAmount, discountValue);
        return CouponDiscount.of(discountAmount);
    }

    public void use(Long userId, ZonedDateTime usedAt) {
        validateOwner(userId);
        validateUsedAt(usedAt);
        validateAvailable();

        this.status = UserCouponStatus.USED;
        this.usedAt = usedAt;
    }

    public void expire() {
        if (status == UserCouponStatus.AVAILABLE) {
            this.status = UserCouponStatus.EXPIRED;
        }
    }

    private static CouponName requireName(CouponName name) {
        if (name == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        return name;
    }

    private static CouponType requireType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
        return type;
    }

    private static DiscountValue requireDiscountValue(DiscountValue discountValue) {
        if (discountValue == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 값은 비어있을 수 없습니다.");
        }
        return discountValue;
    }

    private static CouponExpiration requireExpiration(CouponExpiration expiration) {
        if (expiration == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 비어있을 수 없습니다.");
        }
        return expiration;
    }

    private void validateOwner(Long userId) {
        if (!owner.isSameUser(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 쿠폰은 사용할 수 없습니다.");
        }
    }

    private static void validateUsedAt(ZonedDateTime usedAt) {
        if (usedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 사용 일시는 비어있을 수 없습니다.");
        }
    }

    private void validateAvailable() {
        if (status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.");
        }
    }

    private void validatePolicy(CouponDiscountPolicy policy) {
        if (policy == null || policy.type() != type) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 타입에 맞는 할인 정책이 없습니다.");
        }
    }

    private static void validateOrderAmount(CouponMoney orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 비어있을 수 없습니다.");
        }
    }

    private boolean isExpiredAt(ZonedDateTime now) {
        return expiration.isExpiredAt(now);
    }

    private void validateNotExpired(ZonedDateTime now) {
        if (isExpiredAt(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
    }

    private void validateMinimumOrderAmount(CouponMoney orderAmount) {
        if (!satisfiesMinimumOrderAmount(orderAmount)) {
            throw new CoreException(ErrorType.CONFLICT, "최소 주문 금액을 충족하지 못했습니다.");
        }
    }

    private boolean satisfiesMinimumOrderAmount(CouponMoney orderAmount) {
        return minimumOrderAmount == null || !orderAmount.isLessThan(minimumOrderAmount);
    }
}
