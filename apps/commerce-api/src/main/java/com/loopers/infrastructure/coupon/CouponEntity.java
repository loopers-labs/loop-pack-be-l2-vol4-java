package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * coupon 테이블 JPA 매핑 전용 엔티티 (쿠폰 템플릿). 순수 도메인(CouponModel)과 분리되어 영속 관심사만 담는다.
 * soft delete는 BaseEntity의 deletedAt/delete()/restore()를 사용한다(01 §9 Q3).
 */
@Entity
@Table(name = "coupon")
public class CouponEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponEntity() {}

    public CouponEntity(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    /** 변경 가능한 상태만 갱신 (할인 방식 type은 불변). soft delete 동기화는 별도. */
    public void applyState(String name, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
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
}
