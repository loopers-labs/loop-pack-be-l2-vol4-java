package com.loopers.domain.coupon;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

public class CouponTemplate extends DomainEntity {

    private String name;

    private CouponType type;

    private Long value;

    private Long minOrderAmount;

    private Long totalIssueLimit;

    private Integer maxIssuesPerUser;

    private ZonedDateTime expiredAt;

    public CouponTemplate(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {
        validate(name, type, value, minOrderAmount, totalIssueLimit, maxIssuesPerUser, expiredAt, true);
        apply(name, type, value, minOrderAmount, totalIssueLimit, maxIssuesPerUser, expiredAt);
    }

    public static CouponTemplate reconstruct(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        CouponTemplate couponTemplate = new CouponTemplate();
        couponTemplate.validate(name, type, value, minOrderAmount, totalIssueLimit, maxIssuesPerUser, expiredAt, false);
        couponTemplate.apply(name, type, value, minOrderAmount, totalIssueLimit, maxIssuesPerUser, expiredAt);
        couponTemplate.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return couponTemplate;
    }

    private CouponTemplate() {}

    public void update(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {
        ensureActive();
        validate(name, type, value, minOrderAmount, totalIssueLimit, maxIssuesPerUser, expiredAt, true);
        apply(name, type, value, minOrderAmount, totalIssueLimit, maxIssuesPerUser, expiredAt);
    }

    public void ensureIssuable(ZonedDateTime now) {
        ensureActive();
        if (!expiredAt.isAfter(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰 템플릿은 발급할 수 없습니다.");
        }
    }

    public Long calculateDiscount(Long originalAmount) {
        return calculateDiscount(type, value, minOrderAmount, originalAmount);
    }

    public static Long calculateDiscount(
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long originalAmount
    ) {
        if (originalAmount == null || originalAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
        if (minOrderAmount != null && originalAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 최소 주문 금액을 충족하지 않습니다.");
        }

        long discount = switch (type) {
            case FIXED -> value;
            case RATE -> BigDecimal.valueOf(originalAmount)
                .multiply(BigDecimal.valueOf(value))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        };
        return Math.min(originalAmount, discount);
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public Integer getMaxIssuesPerUser() {
        return maxIssuesPerUser;
    }

    public Long getTotalIssueLimit() {
        return totalIssueLimit;
    }

    public boolean hasTotalIssueLimit() {
        return totalIssueLimit != null;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public boolean isActive() {
        return getDeletedAt() == null;
    }

    private void apply(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.totalIssueLimit = totalIssueLimit;
        this.maxIssuesPerUser = maxIssuesPerUser;
        this.expiredAt = expiredAt;
    }

    private void validate(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Long totalIssueLimit,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt,
        boolean requireFutureExpiration
    ) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 이름은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인값은 1 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 할인율은 100 이하여야 합니다.");
        }
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (totalIssueLimit != null && totalIssueLimit <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "전체 발급 한도는 1 이상이어야 합니다.");
        }
        if (maxIssuesPerUser == null || maxIssuesPerUser <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자별 최대 발급 횟수는 1 이상이어야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 필수입니다.");
        }
        if (requireFutureExpiration && !expiredAt.isAfter(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 현재보다 미래여야 합니다.");
        }
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 쿠폰 템플릿은 사용할 수 없습니다.");
        }
    }
}
