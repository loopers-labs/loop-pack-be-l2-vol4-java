package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class CouponUseService {

    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public Discount useCoupon(Long issuedCouponId, String userId, Long originalAmount) {
        if (issuedCouponId == null) {
            return Discount.none();
        }

        IssuedCoupon issuedCoupon = getIssuedCouponForUpdate(issuedCouponId);
        Long discountAmount = issuedCoupon.use(userId, originalAmount, ZonedDateTime.now());
        issuedCouponRepository.save(issuedCoupon);
        return new Discount(issuedCouponId, discountAmount);
    }

    @Transactional
    public void restoreCoupon(Long issuedCouponId) {
        if (issuedCouponId == null) {
            return;
        }

        IssuedCoupon issuedCoupon = getIssuedCouponForUpdate(issuedCouponId);
        issuedCoupon.restore();
        issuedCouponRepository.save(issuedCoupon);
    }

    private IssuedCoupon getIssuedCouponForUpdate(Long issuedCouponId) {
        if (issuedCouponId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 쿠폰 ID는 1 이상이어야 합니다.");
        }
        return issuedCouponRepository.findForUpdate(issuedCouponId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + issuedCouponId + "] 발급 쿠폰을 찾을 수 없습니다."
            ));
    }

    public record Discount(Long couponId, Long discountAmount) {
        public static Discount none() {
            return new Discount(null, 0L);
        }
    }
}
