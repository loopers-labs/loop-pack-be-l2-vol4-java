package com.loopers.fixture;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponModelFixture {

    private String name = "신규가입 10% 할인";
    private CouponType type = CouponType.RATE;
    private long value = 10L;
    private Long minOrderAmount = null;
    private ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);

    public static CouponModelFixture aCoupon() {
        return new CouponModelFixture();
    }

    public CouponModelFixture withName(String name) { this.name = name; return this; }
    public CouponModelFixture withType(CouponType type) { this.type = type; return this; }
    public CouponModelFixture withValue(long value) { this.value = value; return this; }
    public CouponModelFixture withMinOrderAmount(Long minOrderAmount) { this.minOrderAmount = minOrderAmount; return this; }
    public CouponModelFixture withExpiredAt(ZonedDateTime expiredAt) { this.expiredAt = expiredAt; return this; }

    public CouponModel build() {
        return new CouponModel(name, type, value, minOrderAmount, expiredAt);
    }
}
