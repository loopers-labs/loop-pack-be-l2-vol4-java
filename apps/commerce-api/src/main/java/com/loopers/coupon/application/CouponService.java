package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 쿠폰 템플릿 CRUD 를 담당하는 application 레이어 서비스(Repository 의존). */
@RequiredArgsConstructor
@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public Coupon create(
        String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponRepository.save(new Coupon(name, type, value, minOrderAmount, expiredAt));
    }

    public Coupon get(Long id) {
        return couponRepository
            .find(id)
            .orElseThrow(
                () -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰을 찾을 수 없습니다."));
    }

    public List<Coupon> getAll() {
        return couponRepository.findAll();
    }

    public Map<Long, Coupon> getMapByIds(Collection<Long> ids) {
        return couponRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(Coupon::getId, Function.identity()));
    }

    public Coupon update(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt) {
        Coupon coupon = get(id);
        coupon.update(name, type, value, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    public void delete(Long id) {
        Coupon coupon = get(id);
        coupon.delete();
        couponRepository.save(coupon);
    }
}
