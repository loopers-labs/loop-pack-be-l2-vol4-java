package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * 사용자에게 발급된 쿠폰.
 * @Version 으로 낙관적 락을 적용해 동일 쿠폰의 동시 사용을 방지한다.
 * DB 저장 status 는 AVAILABLE/USED 만 사용하며, EXPIRED 는 expiredAt 기반으로 동적 계산한다.
 */
@Getter
@Entity
@Table(name = "user_coupons", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = {"user_id", "coupon_id"})
})
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false, updatable = false)
    private CouponModel coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private UserCouponStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserCouponModel() {}

    public UserCouponModel(Long userId, CouponModel coupon) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (coupon == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
        this.userId = userId;
        this.coupon = coupon;
        this.status = UserCouponStatus.AVAILABLE;
    }

    /**
     * 쿠폰 사용 처리. @Version 필드가 갱신되어 낙관적 락으로 동시 사용을 차단한다.
     */
    public void use() {
        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
    }

    /**
     * 실제 표시 상태를 반환한다. 만료 여부는 CouponModel.expiredAt 기준으로 동적 계산한다.
     */
    public UserCouponStatus computedStatus() {
        if (this.status == UserCouponStatus.USED) {
            return UserCouponStatus.USED;
        }
        if (coupon.isExpired()) {
            return UserCouponStatus.EXPIRED;
        }
        return UserCouponStatus.AVAILABLE;
    }
}
