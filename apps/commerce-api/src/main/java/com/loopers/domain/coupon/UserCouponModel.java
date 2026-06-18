package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * UserCoupon(발급 쿠폰) Aggregate 루트 — 순수 도메인 객체. 특정 사용자에게 발급된 한 장.
 * 상태는 저장하지 않고 used_at + 템플릿 expired_at으로 파생한다(01 §7.5, 03 §3).
 * 재사용 방지(use는 한 번만)가 핵심 불변식이며, 동시성 보장은 영속 계층의 락(@Version / FOR UPDATE)이 담당한다.
 */
public class UserCouponModel {

    private final Long id;          // 영속 전에는 null
    private final Long userId;
    private final Long couponId;    // 템플릿 ID (Aggregate 간 ID 참조)
    private ZonedDateTime usedAt;   // null이면 미사용
    private final ZonedDateTime issuedAt;

    public UserCouponModel(Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponId는 null일 수 없습니다.");
        }
        this.id = null;
        this.userId = userId;
        this.couponId = couponId;
        this.usedAt = null;
        this.issuedAt = ZonedDateTime.now();
    }

    private UserCouponModel(Long id, Long userId, Long couponId, ZonedDateTime usedAt, ZonedDateTime issuedAt) {
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.usedAt = usedAt;
        this.issuedAt = issuedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static UserCouponModel reconstitute(Long id, Long userId, Long couponId,
                                               ZonedDateTime usedAt, ZonedDateTime issuedAt) {
        return new UserCouponModel(id, userId, couponId, usedAt, issuedAt);
    }

    /**
     * 사용 처리 (01 §7.4). 이미 사용된 쿠폰이면 CONFLICT — 재사용 방지의 도메인 불변식.
     * 동시 요청에서 "정확히 한 번"은 영속 계층 락이 함께 보장한다(UC-20).
     */
    public void use() {
        if (isUsed()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        this.usedAt = ZonedDateTime.now();
    }

    /** 사용 원복 (결제 실패 시, 01 §7.6). 멱등. */
    public void restore() {
        this.usedAt = null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** 소유자 검증 (§2 격리). 불일치 시 호출측이 NOT_FOUND로 응대. */
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 조회 시점의 상태를 파생한다 (01 §3.3, UC-14). 사용됐으면 USED가 우선,
     * 아니면 만료 시각 경과 여부로 EXPIRED / AVAILABLE.
     */
    public UserCouponStatus resolveStatus(ZonedDateTime now, ZonedDateTime expiredAt) {
        if (isUsed()) {
            return UserCouponStatus.USED;
        }
        if (now.isAfter(expiredAt)) {
            return UserCouponStatus.EXPIRED;
        }
        return UserCouponStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }

    public ZonedDateTime getIssuedAt() {
        return issuedAt;
    }
}
