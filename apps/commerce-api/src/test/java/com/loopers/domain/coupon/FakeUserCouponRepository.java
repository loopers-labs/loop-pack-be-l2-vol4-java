package com.loopers.domain.coupon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null || userCoupon.getId() == 0L) {
            FakeCouponTemplateRepository.assignId(userCoupon, sequence.incrementAndGet());
        }
        store.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserCoupon> findAllByUserId(Long userId) {
        List<UserCoupon> result = new ArrayList<>();
        for (UserCoupon c : store.values()) {
            if (c.getUserId().equals(userId)) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public List<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, int page, int size) {
        List<UserCoupon> all = new ArrayList<>();
        for (UserCoupon c : store.values()) {
            if (c.getCouponTemplateId().equals(couponTemplateId)) {
                all.add(c);
            }
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    @Override
    public Optional<UserCoupon> findForUpdate(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
        return store.values().stream()
            .anyMatch(c -> c.getUserId().equals(userId) && c.getCouponTemplateId().equals(couponTemplateId));
    }
}
