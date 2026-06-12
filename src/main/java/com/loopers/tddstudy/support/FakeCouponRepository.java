package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.coupon.Coupon;
import com.loopers.tddstudy.domain.coupon.CouponRepository;
import com.loopers.tddstudy.domain.coupon.UserCoupon;

import java.lang.reflect.Field;
import java.util.*;

public class FakeCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new HashMap<>();
    private long sequence = 1L;

    @Override
    public Coupon save(Coupon coupon) {
        if (getId(coupon) == null) {
            setId(coupon, sequence++);
        }
        store.put(getId(coupon), coupon);
        return coupon;
    }

    private Long getId(Coupon coupon) {
        try {
            Field field = Coupon.class.getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(coupon);
        } catch (Exception e) {
            throw new RuntimeException("Coupon id 접근 실패", e);
        }
    }

    private void setId(Coupon coupon, Long id) {
        try {
            Field field = Coupon.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(coupon, id);
        } catch (Exception e) {
            throw new RuntimeException("Coupon id 설정 실패", e);
        }
    }


    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
