package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * Coupon(쿠폰 템플릿) Aggregate 루트 — 순수 도메인 객체. 할인 방식·값·조건을 보유하고
 * 할인 계산(calculateDiscount)과 적용 가능 검증(ensureUsableAt)을 책임진다.
 * JPA 매핑은 infrastructure.coupon.CouponEntity, 변환은 CouponEntityMapper가 담당한다(방식2).
 */
public class CouponModel {

    private static final int NAME_MAX_LENGTH = 100;
    private static final long RATE_MAX_PERCENT = 100L;

    private final Long id;   // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private String name;
    private CouponType type;
    private long value;
    private Long minOrderAmount;       // nullable — null이면 최소 주문 금액 조건 없음 (04 §2.1)
    private ZonedDateTime expiredAt;
    private ZonedDateTime deletedAt;   // null이면 활성 (soft delete, 01 §9 Q3)

    public CouponModel(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.id = null;
        this.name = validateName(name);
        this.type = validateType(type);
        this.value = validateValue(type, value);
        this.minOrderAmount = validateMinOrderAmount(minOrderAmount);
        this.expiredAt = validateExpiredAt(expiredAt);
        this.deletedAt = null;
    }

    private CouponModel(Long id, String name, CouponType type, long value, Long minOrderAmount,
                        ZonedDateTime expiredAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.deletedAt = deletedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static CouponModel reconstitute(Long id, String name, CouponType type, long value,
                                           Long minOrderAmount, ZonedDateTime expiredAt, ZonedDateTime deletedAt) {
        return new CouponModel(id, name, type, value, minOrderAmount, expiredAt, deletedAt);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 null이거나 공백일 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return name;
    }

    private static CouponType validateType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 방식은 null일 수 없습니다.");
        }
        return type;
    }

    private static long validateValue(CouponType type, long value) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value > RATE_MAX_PERCENT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인 값은 " + RATE_MAX_PERCENT + "% 이하여야 합니다.");
        }
        return value;
    }

    private static Long validateMinOrderAmount(Long minOrderAmount) {
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        return minOrderAmount;
    }

    private static ZonedDateTime validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 null일 수 없습니다.");
        }
        return expiredAt;
    }

    /**
     * 적용 전 금액에 대한 할인 금액 (01 §7.1). 방식별 계산은 CouponType이 캡슐화한다.
     * 결과는 0 ≤ discount ≤ originalAmount 범위(최종 결제 금액 음수 불가).
     */
    public long calculateDiscount(long originalAmount) {
        return type.discount(value, originalAmount);
    }

    /**
     * 적용 직전 검증의 단일 진입점 (01 §7.2). 만료됐거나 최소 주문 금액 미달이면 BAD_REQUEST.
     */
    public void ensureUsableAt(ZonedDateTime now, long originalAmount) {
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (minOrderAmount != null && originalAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액(" + minOrderAmount + ")을 충족하지 않습니다.");
        }
    }

    /** 만료 시각이 지났으면 true (01 §7.5, 경계: 만료 시각과 같으면 아직 유효). */
    public boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    /** 활성 여부 — deletedAt이 null이면 활성 (01 §9 Q3). */
    public boolean isActive() {
        return deletedAt == null;
    }

    /** soft delete. 멱등. */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /** soft delete 복원. 멱등. */
    public void restore() {
        this.deletedAt = null;
    }

    /** 템플릿 수정 (UC-15 Admin). 생성과 동일한 검증을 적용한다. 할인 방식(type)은 변경하지 않는다. */
    public void update(String name, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.name = validateName(name);
        this.value = validateValue(this.type, value);
        this.minOrderAmount = validateMinOrderAmount(minOrderAmount);
        this.expiredAt = validateExpiredAt(expiredAt);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
