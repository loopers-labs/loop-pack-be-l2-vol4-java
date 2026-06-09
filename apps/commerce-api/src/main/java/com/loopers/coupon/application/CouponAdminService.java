package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponAdminService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponResult.Detail create(CouponCommand.Create command) {
        Coupon coupon = Coupon.create(
                command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt()
        );
        return CouponResult.Detail.from(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResult.Detail update(CouponCommand.Update command) {
        Coupon coupon = get(command.couponId());
        coupon.update(
                command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt()
        );
        return CouponResult.Detail.from(coupon);
    }

    @Transactional
    public void delete(Long couponId) {
        get(couponId).delete();
    }

    @Transactional(readOnly = true)
    public CouponResult.Detail getCoupon(Long couponId) {
        return CouponResult.Detail.from(get(couponId));
    }

    @Transactional(readOnly = true)
    public Page<CouponResult.Detail> getCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable).map(CouponResult.Detail::from);
    }

    @Transactional(readOnly = true)
    public Page<CouponResult.IssueDetail> getIssues(Long couponId, Pageable pageable) {
        get(couponId);
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findByCouponId(couponId, pageable)
                .map(userCoupon -> CouponResult.IssueDetail.from(userCoupon, now));
    }

    private Coupon get(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, CouponErrorCode.COUPON_NOT_FOUND));
    }
}
