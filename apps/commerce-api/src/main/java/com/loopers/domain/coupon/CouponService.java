package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 쿠폰 템플릿(Coupon) 생명주기 — Admin 등록/수정/조회/삭제(UC-15)와 발급 가능 검증(UC-13).
 */
@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponModel register(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponRepository.save(new CouponModel(name, type, value, minOrderAmount, expiredAt));
    }

    /** 템플릿 상세 — 존재하지 않으면 NOT_FOUND (Admin/내부 공통). */
    @Transactional(readOnly = true)
    public CouponModel getCoupon(Long id) {
        return couponRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰을 찾을 수 없습니다."));
    }

    /**
     * 발급 가능한 템플릿 — 존재하고 활성이며 만료되지 않은 것 (UC-13).
     * 미존재 → NOT_FOUND, 비활성/만료 → BAD_REQUEST (§9 Q2).
     */
    @Transactional(readOnly = true)
    public CouponModel getIssuableTemplate(Long id, ZonedDateTime now) {
        CouponModel coupon = getCoupon(id);
        if (!coupon.isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        if (coupon.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        return coupon;
    }

    /** 주문 적용 시 발급분이 가리키는 템플릿 — 활성이어야 사용 가능 (§9 Q3, soft delete된 템플릿은 적용 불가). */
    @Transactional(readOnly = true)
    public CouponModel getActiveTemplate(Long id) {
        CouponModel coupon = getCoupon(id);
        if (!coupon.isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        return coupon;
    }

    @Transactional(readOnly = true)
    public List<CouponModel> getCoupons(int page, int size) {
        PagePolicy.validate(page, size);
        return couponRepository.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public List<CouponModel> findByIds(Collection<Long> ids) {
        return couponRepository.findByIds(ids);
    }

    /** 템플릿 수정 (UC-15). 활성 템플릿만 — 부재/비활성이면 NOT_FOUND. */
    @Transactional
    public CouponModel update(Long id, String name, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponModel coupon = getCoupon(id);
        if (!coupon.isActive()) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰을 찾을 수 없습니다.");
        }
        coupon.update(name, value, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    /** 템플릿 soft delete (UC-15, §9 Q3). 멱등. */
    @Transactional
    public void deleteCoupon(Long id) {
        CouponModel coupon = getCoupon(id);
        coupon.delete();
        couponRepository.save(coupon);
    }
}
