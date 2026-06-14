package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저에게 발급된 쿠폰 한 장. 어떤 템플릿(couponId)을 누가(userId) 가졌고 상태가 무엇인지를 가진다.
 * 발급 시점에는 AVAILABLE 로 시작한다.
 * userId/couponId 는 인증·검증된 흐름에서 오는 내부 보장값이므로 @Column(nullable=false) 에 맡긴다.
 */
@Entity
@Table(name = "user_coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CouponStatus status;

    /**
     * 낙관적 락. 동일 쿠폰으로 동시 주문 시 먼저 커밋한 한 건만 성공하고 나머지는 충돌 예외로 실패한다.
     */
    @Version
    private Long version;

    public UserCoupon(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponStatus.AVAILABLE;
    }

    /**
     * 조회 시점의 노출 상태를 계산한다. 저장된 status(AVAILABLE/USED)와 템플릿 만료일을 조합한다.
     * EXPIRED 는 저장하지 않고 여기서 파생한다.
     */
    public CouponStatus resolveStatus(LocalDateTime expiredAt, LocalDateTime now) {
        if (status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        if (now.isAfter(expiredAt)) {
            return CouponStatus.EXPIRED;
        }
        return CouponStatus.AVAILABLE;
    }

    public void use(LocalDateTime expiredAt, LocalDateTime now) {
        CouponStatus current = resolveStatus(expiredAt, now);
        if (current == CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (current == CouponStatus.EXPIRED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
    }
}
