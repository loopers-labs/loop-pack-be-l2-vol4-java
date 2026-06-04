package com.loopers.domain.coupon;

import com.loopers.domain.coupon.policy.CouponDiscountPolicy;
import com.loopers.domain.coupon.policy.CouponDiscountMethod;
import com.loopers.domain.coupon.specification.CouponUseAttempt;
import com.loopers.domain.coupon.specification.UsableCouponSpecification;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponDiscountMethod couponDiscountMethod;
    private final UsableCouponSpecification usableCouponSpecification;

    @Transactional
    public CouponIssueResult issueCoupon(Long userId, Long couponTemplateId) {
        CouponTemplate couponTemplate = getCouponTemplate(couponTemplateId);
        if (userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }

        UserCoupon issuedCoupon = couponTemplate.issue(userId);
        UserCoupon savedCoupon = userCouponRepository.save(issuedCoupon);
        return CouponIssueResult.issued(savedCoupon);
    }

    public CouponTemplate getCouponTemplate(Long couponTemplateId) {
        return couponTemplateRepository.findIssuingCoupon(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional
    public CouponDiscount applyToOrder(CouponUseCommand command) {
        if (!command.hasCoupon()) {
            return CouponDiscount.none(command.orderAmount());
        }

        UserCoupon userCoupon = userCouponRepository.findById(command.userCouponId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        CouponUseAttempt attempt = command.toAttempt(userCoupon);
        usableCouponSpecification.confirmUsable(attempt);

        CouponDiscountPolicy policy = couponDiscountMethod.match(userCoupon.getType());
        CouponDiscount discount = userCoupon.apply(command.orderAmount(), command.usedAt(), policy);

        boolean couponUsed = userCouponRepository.useAvailableCoupon(
            command.userCouponId(),
            command.userId(),
            command.usedAt()
        );
        if (!couponUsed) {
            throw new CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.");
        }

        return discount;
    }
}
