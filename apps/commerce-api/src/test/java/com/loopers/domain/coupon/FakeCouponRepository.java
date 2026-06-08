package com.loopers.domain.coupon;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 CouponRepository.
 */
public class FakeCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null || coupon.getId() == 0L) {
            ReflectionTestUtils.setField(coupon, "id", sequence.incrementAndGet());
        }
        store.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Coupon> findAll(int page, int size) {
        List<Coupon> all = new ArrayList<>(store.values());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && store.containsKey(id);
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }
}
