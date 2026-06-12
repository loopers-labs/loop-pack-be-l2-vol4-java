package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

public class UserCouponModel {

	private Long id;
	private Long userId;
	private Long couponId;
	private CouponStatus status;
	private Long version;
	private ZonedDateTime createdAt;
	private ZonedDateTime updatedAt;

	protected UserCouponModel() {}

	public UserCouponModel(Long userId, Long couponId) {
		this.userId = userId;
		this.couponId = couponId;
		this.status = CouponStatus.AVAILABLE;
	}

	public UserCouponModel(Long id, Long userId, Long couponId, CouponStatus status, Long version, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.couponId = couponId;
		this.status = status;
		this.version = version;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public void use() {
		if (status != CouponStatus.AVAILABLE) {
			throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰이 아닙니다.");
		}
		this.status = CouponStatus.USED;
	}

	public boolean isUsable(ZonedDateTime expiredAt) {
		return status == CouponStatus.AVAILABLE && ZonedDateTime.now().isBefore(expiredAt);
	}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public Long getCouponId() { return couponId; }
	public CouponStatus getStatus() { return status; }
	public Long getVersion() { return version; }
	public ZonedDateTime getCreatedAt() { return createdAt; }
	public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
