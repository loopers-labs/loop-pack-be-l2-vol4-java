package com.loopers.tddstudy.domain.coupon;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupon")
public class UserCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private UserCouponStatus status;

    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;

    protected UserCoupon() {}

    public UserCoupon(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = LocalDateTime.now();
    }

    public void use() {
        if (status != UserCouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용 불가능한 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public boolean isAvailable() {
        return status == UserCouponStatus.AVAILABLE;
    }



    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getCouponId() { return couponId; }
    public UserCouponStatus getStatus() { return status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
}
