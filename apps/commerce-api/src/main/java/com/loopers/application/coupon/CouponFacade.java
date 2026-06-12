package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponInfo issue(Long userId, Long couponId) {
        CouponModel template = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));

        UserCouponModel issued = template.issue(userId);
        UserCouponModel saved = userCouponRepository.save(issued);
        return CouponInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now();   // 만료 파생 기준 시각
        return userCouponRepository.findByUserId(userId).stream()
                .map(uc -> CouponInfo.from(uc, now))
                .toList();
    }
}
