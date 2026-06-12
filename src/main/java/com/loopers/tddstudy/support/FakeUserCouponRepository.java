package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.coupon.UserCoupon;
import com.loopers.tddstudy.domain.coupon.UserCouponRepository;
import java.lang.reflect.Field;

import java.util.*;

public class FakeUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new HashMap<>();
    private long sequence = 1L;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (getId(userCoupon) == null) {
            setId(userCoupon, sequence++);
        }
        store.put(getId(userCoupon), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<UserCoupon> findByIdWithLock(Long id) {
        return findById(id); // Fake라서 락 없이 그냥 조회
    }

    @Override
    public List<UserCoupon> findAllByUserId(Long userId) {
        return store.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .toList();
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
                .anyMatch(uc -> uc.getUserId().equals(userId) && uc.getCouponId().equals(couponId));
    }

    @Override
    public List<UserCoupon> findAllByCouponId(Long couponId) {
        return store.values().stream()
                .filter(uc -> uc.getCouponId().equals(couponId))
                .toList();
    }

    private Long getId(UserCoupon userCoupon) {
        try {
            Field field = UserCoupon.class.getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(userCoupon);
        } catch (Exception e) {
            throw new RuntimeException("UserCoupon id 접근 실패", e);
        }
    }

    private void setId(UserCoupon userCoupon, Long id) {
        try {
            Field field = UserCoupon.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(userCoupon, id);
        } catch (Exception e) {
            throw new RuntimeException("UserCoupon id 설정 실패", e);
        }
    }
}
