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

import java.time.ZonedDateTime;

/**
 * 회원에게 발급된 쿠폰. 사용 상태(AVAILABLE/USED/EXPIRED)를 관리하는 애그리거트 루트.
 *
 * <p>한 번만 사용될 수 있으며, 동시 사용은 저장소 계층의 비관적 락(findByIdForUpdate)으로 직렬화한다.
 */
@Getter
@Entity
@Table(name = "member_coupon")
public class MemberCoupon extends BaseEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "coupon_id", nullable = false, updatable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private CouponState state;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "issued_at", nullable = false)
    private ZonedDateTime issuedAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected MemberCoupon() {}

    public MemberCoupon(
        Long memberId, Long couponId, ZonedDateTime expiredAt, ZonedDateTime issuedAt) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원은 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일시는 필수입니다.");
        }
        this.memberId = memberId;
        this.couponId = couponId;
        this.expiredAt = expiredAt;
        this.issuedAt = issuedAt;
        this.state = CouponState.AVAILABLE;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    /** 표시용 상태. 저장값이 AVAILABLE 이어도 만료 시각이 지났으면 EXPIRED 로 보여준다(저장값은 변경하지 않음). */
    public CouponState currentState(ZonedDateTime now) {
        if (state == CouponState.AVAILABLE && isExpired(now)) {
            return CouponState.EXPIRED;
        }
        return state;
    }

    /** 주문에 사용 처리한다. 이미 사용했거나 만료된 경우 예외를 던진다. */
    public void use(Long orderId, ZonedDateTime now) {
        if (state == CouponState.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
        this.state = CouponState.USED;
        this.orderId = orderId;
        this.usedAt = now;
    }
}
