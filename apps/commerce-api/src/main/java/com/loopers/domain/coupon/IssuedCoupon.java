package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class IssuedCoupon {

    private Long id;
    private Long couponId;
    private String userLoginId;
    private CouponStatus status;
    private ZonedDateTime expiredAt;
    private ZonedDateTime usedAt;

    public IssuedCoupon(Long couponId, String userLoginId, ZonedDateTime expiredAt) {
        this(null, couponId, userLoginId, CouponStatus.AVAILABLE, expiredAt, null);
    }

    private IssuedCoupon(
        Long id,
        Long couponId,
        String userLoginId,
        CouponStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        validateCouponId(couponId);
        validateUserLoginId(userLoginId);
        validateStatus(status);
        validateExpiredAt(expiredAt);

        this.id = id;
        this.couponId = couponId;
        this.userLoginId = userLoginId;
        this.status = status;
        this.expiredAt = expiredAt;
        this.usedAt = usedAt;
    }

    public static IssuedCoupon reconstruct(
        Long id,
        Long couponId,
        String userLoginId,
        CouponStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        return new IssuedCoupon(id, couponId, userLoginId, status, expiredAt, usedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getUserLoginId() {
        return userLoginId;
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

    public CouponStatus currentStatus(ZonedDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 시각은 비어있을 수 없습니다.");
        }
        if (status == CouponStatus.AVAILABLE && isExpired(now)) {
            return CouponStatus.EXPIRED;
        }
        return status;
    }

    public void use(String userLoginId, ZonedDateTime now) {
        validateUserLoginId(userLoginId);
        if (!this.userLoginId.equals(userLoginId)) {
            throw new CoreException(ErrorType.CONFLICT, "다른 회원의 쿠폰은 사용할 수 없습니다.");
        }
        if (currentStatus(now) != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.");
        }

        this.status = CouponStatus.USED;
        this.usedAt = now;
    }

    private boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now) || expiredAt.isEqual(now);
    }

    private void validateCouponId(Long couponId) {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateUserLoginId(String userLoginId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 로그인 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateStatus(CouponStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 상태는 비어있을 수 없습니다.");
        }
    }

    private void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료 일시는 비어있을 수 없습니다.");
        }
    }
}
