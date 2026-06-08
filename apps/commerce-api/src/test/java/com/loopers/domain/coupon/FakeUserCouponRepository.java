package com.loopers.domain.coupon;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 UserCouponRepository. (user_id, coupon_id) UNIQUE 도 흉내낸다.
 */
public class FakeUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null || userCoupon.getId() == 0L) {
            ReflectionTestUtils.setField(userCoupon, "id", sequence.incrementAndGet());
        }
        store.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
            .filter(uc -> uc.getUserId().equals(userId) && uc.getCouponId().equals(couponId))
            .findFirst();
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        List<UserCoupon> result = new ArrayList<>();
        for (UserCoupon uc : store.values()) {
            if (uc.getUserId().equals(userId)) {
                result.add(uc);
            }
        }
        return result;
    }

    @Override
    public List<UserCoupon> findByCouponId(Long couponId, int page, int size) {
        List<UserCoupon> all = new ArrayList<>();
        for (UserCoupon uc : store.values()) {
            if (uc.getCouponId().equals(couponId)) {
                all.add(uc);
            }
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }
}
