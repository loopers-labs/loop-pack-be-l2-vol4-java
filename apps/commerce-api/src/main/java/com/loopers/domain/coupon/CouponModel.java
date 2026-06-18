package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

public class CouponModel {

	private Long id;
	private String name;
	private CouponType type;
	private int value;
	private int minOrderAmount;
	private ZonedDateTime expiredAt;
	private ZonedDateTime createdAt;
	private ZonedDateTime updatedAt;

	protected CouponModel() {}

	public CouponModel(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
		validate(name, type, value, minOrderAmount, expiredAt);
		this.name = name;
		this.type = type;
		this.value = value;
		this.minOrderAmount = minOrderAmount;
		this.expiredAt = expiredAt;
	}

	public CouponModel(Long id, String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
		validate(name, type, value, minOrderAmount, expiredAt);
		this.id = id;
		this.name = name;
		this.type = type;
		this.value = value;
		this.minOrderAmount = minOrderAmount;
		this.expiredAt = expiredAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	private void validate(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
		if (name == null || name.isBlank()) {
			throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
		}
		if (type == null) {
			throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
		}
		if (type == CouponType.RATE && (value <= 0 || value > 100)) {
			throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 1~100 사이여야 합니다.");
		}
		if (type == CouponType.FIXED && value <= 0) {
			throw new CoreException(ErrorType.BAD_REQUEST, "정액 쿠폰의 할인 금액은 0보다 커야 합니다.");
		}
		if (minOrderAmount < 0) {
			throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
		}
		if (expiredAt == null) {
			throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 필수입니다.");
		}
	}

	public void update(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
		validate(name, type, value, minOrderAmount, expiredAt);
		this.name = name;
		this.type = type;
		this.value = value;
		this.minOrderAmount = minOrderAmount;
		this.expiredAt = expiredAt;
	}

	public long calculateDiscount(long orderAmount) {
		if (orderAmount < minOrderAmount) {
			throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다. 최소 주문 금액: " + minOrderAmount);
		}
		if (type == CouponType.FIXED) {
			return value;
		}
		return orderAmount * value / 100;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public CouponType getType() { return type; }
	public int getValue() { return value; }
	public int getMinOrderAmount() { return minOrderAmount; }
	public ZonedDateTime getExpiredAt() { return expiredAt; }
	public ZonedDateTime getCreatedAt() { return createdAt; }
	public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
