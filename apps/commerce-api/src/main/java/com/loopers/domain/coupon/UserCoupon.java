package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 발급된 사용자 쿠폰 — 한 행은 1 회만 사용 가능.
 * status 전이 AVAILABLE → USED 는 단 한 번만 허용된다.
 */
@Entity
@Table(name = "user_coupons")
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected UserCoupon() {}

    public UserCoupon(Long userId, Long couponTemplateId, LocalDateTime issuedAt) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
        if (couponTemplateId == null || couponTemplateId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID 는 양수여야 합니다.");
        }
        if (issuedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 시각은 필수입니다.");
        }
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = issuedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public UserCouponStatus getStatus() {
        return status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    /**
     * 사용자가 본 쿠폰의 소유자인지 검증.
     */
    public void assertOwnedBy(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "본인 소유의 쿠폰이 아닙니다.");
        }
    }

    /**
     * 사용 가능 상태로 전이된 쿠폰만 사용한다.
     * 이미 USED/EXPIRED 면 CONFLICT — 비관적 락 + 본 메서드 조합으로 1 회성 보장.
     */
    public void use(LocalDateTime at) {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT,
                "이미 사용 또는 만료된 쿠폰입니다. (현재 상태: " + this.status + ")");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = at;
    }

    /**
     * 만료 처리 — 스케줄러 등에서 호출. 이미 USED 면 그대로 둔다.
     */
    public void expire() {
        if (this.status == UserCouponStatus.AVAILABLE) {
            this.status = UserCouponStatus.EXPIRED;
        }
    }
}
