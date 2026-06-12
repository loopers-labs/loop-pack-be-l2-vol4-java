package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿(정의). ADMIN 이 등록·관리한다.
 * 이 템플릿 1개로 여러 UserCoupon(발급분)이 만들어진다.
 */
@Entity
@Table(name = "coupon")
@SQLRestriction("deleted_at IS NULL")
public class CouponModel extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 100;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Embedded
    private DiscountPolicy discountPolicy;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponModel() {}

    private CouponModel(String name, DiscountPolicy discountPolicy, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 " + NAME_MAX_LENGTH + "자 이내여야 합니다.");
        }
        if (discountPolicy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 정책은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.expiredAt = expiredAt;
    }

    public static CouponModel create(String name, DiscountPolicy discountPolicy, ZonedDateTime expiredAt) {
        return new CouponModel(name, discountPolicy, expiredAt);
    }

    /** 템플릿 수정 (ADMIN). 검증은 생성과 동일 규칙. */
    public void update(String name, DiscountPolicy discountPolicy, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 " + NAME_MAX_LENGTH + "자 이내여야 합니다.");
        }
        if (discountPolicy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 정책은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.expiredAt = expiredAt;
    }

    /**
     * 이 템플릿으로부터 유저에게 줄 발급분을 찍어낸다.
     * 할인 규칙·만료일을 발급 시점에 스냅샷으로 복사한다.
     */
    public UserCouponModel issue(Long userId) {
        return UserCouponModel.issue(userId, this.getId(), this.discountPolicy, this.expiredAt);
    }

    public String getName() { return name; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }
}
