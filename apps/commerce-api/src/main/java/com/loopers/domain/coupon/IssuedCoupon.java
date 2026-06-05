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
import lombok.Getter;

import java.time.ZonedDateTime;

/** 유저에게 발급된 쿠폰. 한 유저가 같은 템플릿을 여러 장 발급받을 수 있다. CouponTemplate은 다른 애그리거트라 ID 참조한다. */
@Getter
@Entity
@Table(name = "issued_coupon")
public class IssuedCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    /** 낙관적 락 — 동일 쿠폰 동시 사용 시 한 트랜잭션만 커밋되고 나머지는 충돌 예외로 실패시킨다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected IssuedCoupon() {}

    public IssuedCoupon(Long userId, Long couponTemplateId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 비어있을 수 없습니다.");
        }
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = CouponStatus.AVAILABLE;
    }

    /** 재사용 불가 불변식 — AVAILABLE이 아니면 사용할 수 없다. 만료 검증은 템플릿을 아는 호출 측 책임이다. */
    public void use() {
        if (this.status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용했거나 사용할 수 없는 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 목록 조회용 표시 상태. USED면 그대로, AVAILABLE이어도 템플릿이 만료됐으면 EXPIRED로 본다. */
    public CouponStatus displayStatus(CouponTemplate template, ZonedDateTime at) {
        if (this.status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        return template.isExpired(at) ? CouponStatus.EXPIRED : CouponStatus.AVAILABLE;
    }
}
