package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발급된 쿠폰 인스턴스(사용자 소유). (user_id, coupon_id) UNIQUE 로 1인 1장을 강제한다.
 * 동시 사용 race condition 은 @Version(낙관적 락) 으로 차단한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "user_coupon",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_coupon_user_id_coupon_id",
                                          columnNames = {"user_id", "coupon_id"})
)
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    /** 사용 시각. AVAILABLE 일 땐 null. */
    private LocalDateTime usedAt;

    /** 발급 시각. BaseEntity.createdAt 과 별개로 도메인 의미를 명확히 하기 위해 보관. */
    private LocalDateTime issuedAt;

    @Version
    private Long version;

    private UserCoupon(Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId 는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponId 는 필수입니다.");
        }
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponStatus.AVAILABLE;
        this.issuedAt = LocalDateTime.now();
    }

    public static UserCoupon issue(Long userId, Long couponId) {
        return new UserCoupon(userId, couponId);
    }

    /**
     * 쿠폰을 사용 처리한다. AVAILABLE 이 아니면 예외.
     * 동시 호출 시 @Version 충돌로 한 트랜잭션만 성공한다.
     */
    public void use() {
        if (this.status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public boolean belongsTo(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isAvailable() {
        return this.status == CouponStatus.AVAILABLE;
    }
}
