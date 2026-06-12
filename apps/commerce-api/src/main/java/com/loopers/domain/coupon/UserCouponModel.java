package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

/**
 * 발급된 사용자 쿠폰 (실제 주문에 사용되는 인스턴스).
 *
 * <p><strong>혜택 스냅샷 — 발급은 그 시점의 약속</strong>:
 * 발급 시점 템플릿의 혜택(이름/할인 타입/할인 값/최소주문금액/만료 시각)을 통째로 복사해 둔다.
 * 어드민이 이후 템플릿을 수정해도 이미 발급된 쿠폰의 혜택은 변하지 않으며,
 * 사용/조회 시 템플릿을 재조회할 필요도 없다. 템플릿 수정은 이후 발급분에만 영향을 준다.
 *
 * <p><strong>동시성 — 낙관적 락(@Version)</strong>:
 * 동일 쿠폰으로 여러 기기에서 동시에 주문해도 단 한 번만 사용되어야 한다.
 * "경쟁자 중 1명만 성공" 설계에는 낙관적 락이 자연스럽다. 두 트랜잭션이 같은 version 을 읽고
 * USED 로 변경하면, 커밋 시 한 쪽만 성공하고 다른 쪽은 {@code OptimisticLockException} 으로 실패한다.
 *
 * <p>{@code (user_id, coupon_id)} 복합 UK 로 동일 템플릿 중복 발급을 DB 레벨에서 방지한다.
 */
@Entity
@Table(name = "user_coupons", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = {"user_id", "coupon_id"})
})
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    /** 발급 시점 템플릿 이름 스냅샷. */
    @Column(name = "coupon_name", nullable = false)
    private String couponName;

    /** 발급 시점 할인 타입 스냅샷. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private CouponType type;

    /** 발급 시점 할인 값 스냅샷. FIXED=할인금액(원), RATE=할인율(%). */
    @Column(name = "discount_value", nullable = false)
    private long value;

    /** 발급 시점 최소 주문 금액 스냅샷. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    private Long version;

    protected UserCouponModel() {}

    public UserCouponModel(Long userId, Long couponId, String couponName, CouponType type,
                           long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponId는 필수입니다.");
        }
        if (couponName == null || couponName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
        type.validateValue(value);
        this.userId = userId;
        this.couponId = couponId;
        this.couponName = couponName;
        this.type = type;
        this.value = value;
        this.minOrderAmount = Money.of(minOrderAmount == null ? 0L : minOrderAmount);
        this.expiredAt = expiredAt;
        this.status = CouponStatus.AVAILABLE;
    }

    /** 발급 — 템플릿의 혜택을 통째로 스냅샷한다. */
    public static UserCouponModel issue(Long userId, CouponModel coupon) {
        return new UserCouponModel(
            userId,
            coupon.getId(),
            coupon.getName(),
            coupon.getType(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt()
        );
    }

    /**
     * 이 쿠폰이 주어진 주문 금액·시점에 적용 가능한지 검증한다 (발급 스냅샷 기준).
     *
     * <p>만료 여부와 최소 주문 금액 조건을 한 곳에서 명시적으로 확인한다.
     * {@link #calculateDiscount} 호출 전에 반드시 먼저 호출해야 한다.
     */
    public void validateApplicable(Money orderAmount, ZonedDateTime now) {
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (!orderAmount.isGreaterThanOrEqual(minOrderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount.getAmount() + "원) 조건을 충족하지 못해 쿠폰을 사용할 수 없습니다.");
        }
    }

    /**
     * 주문 금액에 대한 할인 금액을 계산한다 (발급 스냅샷 기준).
     *
     * <p>반드시 {@link #validateApplicable} 호출 이후에 사용해야 한다.
     */
    public Money calculateDiscount(Money orderAmount) {
        return type.calculateDiscount(orderAmount, value);
    }

    /**
     * 쿠폰을 사용 처리한다 (AVAILABLE → USED).
     *
     * <p>만료/최소주문금액 검증은 {@link #validateApplicable}이 책임진다.
     * 여기서는 중복 사용(status) 검증과 상태 전이만 수행한다.
     * 동시 사용 충돌(같은 쿠폰 동시 주문)은 {@code @Version} 낙관적 락이 커밋 시점에 한 건만 통과시킨다.
     */
    public void use(ZonedDateTime now) {
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = now;
    }

    /**
     * 사용 처리를 되돌린다 (USED → AVAILABLE). 결제 실패 보상 트랜잭션 전용.
     *
     * <p>소프트 삭제 복원({@code BaseEntity#restore})과는 무관하다.
     */
    public void cancelUse() {
        if (this.status != CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 사용 취소할 수 있습니다.");
        }
        this.status = CouponStatus.AVAILABLE;
        this.usedAt = null;
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 조회 시점 기준 표시 상태. USED 가 아니면서 만료 시각이 지났으면 EXPIRED 로 보여준다.
     * (저장 상태는 AVAILABLE/USED 만 가지며 EXPIRED 는 파생값)
     */
    public CouponStatus displayStatus(ZonedDateTime now) {
        if (this.status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        return isExpired(now) ? CouponStatus.EXPIRED : CouponStatus.AVAILABLE;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getCouponName() {
        return couponName;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    /** 최소 주문 금액 (DTO/응답용 — Long). */
    public Long getMinOrderAmount() {
        return minOrderAmount.getAmount();
    }

    public CouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }
}
