package com.loopers.tddstudy.domain.coupon;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private CouponType type;

    @Column(name = "discount_value")
    private int value;          // FIXED: 할인금액(원), RATE: 할인율(%)

    private int minOrderAmount;  // 최소 주문금액 (0이면 조건 없음)
    private LocalDateTime expiredAt;

    protected Coupon() {}

    public Coupon(String name, CouponType type, int value, int minOrderAmount, LocalDateTime expiredAt) {
        // 기본 유효성 검증
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public int discount(int orderAmount) {
        if (orderAmount < minOrderAmount) {
            throw new IllegalArgumentException("최소 주문금액을 충족하지 않습니다.");
        }
        if (type == CouponType.FIXED) {
            return Math.min(value, orderAmount);  // 주문금액보다 클 수 없음
        } else {
            return orderAmount * value / 100;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    public void update(String name, int value, int minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }


    public Long getId() { return id; }
    public String getName() { return name; }
    public CouponType getType() { return type; }
    public int getValue() { return value; }
    public int getMinOrderAmount() { return minOrderAmount; }
    public LocalDateTime getExpiredAt() { return expiredAt; }


}
