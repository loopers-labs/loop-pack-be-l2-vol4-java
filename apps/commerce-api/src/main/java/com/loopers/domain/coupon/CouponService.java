package com.loopers.domain.coupon;

import com.loopers.domain.coupon.policy.CouponDiscountPolicy;
import com.loopers.domain.coupon.policy.CouponDiscountMethod;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponDiscountMethod couponDiscountMethod;

    @Transactional
    public CouponTemplate createCoupon(
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt
    ) {
        CouponDiscountPolicy policy = couponDiscountMethod.match(type);
        CouponTemplate coupon = CouponTemplate.create(name, type, discountValue, minimumOrderAmount, expiredAt, policy);
        return couponTemplateRepository.save(coupon);
    }

    @Transactional
    public CouponTemplate updateCoupon(
        Long couponTemplateId,
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt
    ) {
        CouponDiscountPolicy policy = couponDiscountMethod.match(type);
        CouponTemplate coupon = getCouponTemplate(couponTemplateId);
        coupon.update(name, type, discountValue, minimumOrderAmount, expiredAt, policy);
        return coupon;
    }

    @Transactional
    public CouponIssueResult issueCoupon(Long userId, Long couponTemplateId) {
        CouponTemplate couponTemplate = getCouponTemplate(couponTemplateId);
        if (userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }

        UserCoupon issuedCoupon = couponTemplate.issue(userId, ZonedDateTime.now());
        UserCoupon savedCoupon = userCouponRepository.save(issuedCoupon);
        return CouponIssueResult.issued(savedCoupon);
    }

    public CouponTemplate getCouponTemplate(Long couponTemplateId) {
        return couponTemplateRepository.findActiveById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional(readOnly = true)
    public PageResult<CouponTemplate> getCoupons(PageQuery query) {
        return couponTemplateRepository.findActiveAll(query);
    }

    @Transactional(readOnly = true)
    public PageResult<UserCoupon> getCouponIssues(Long couponTemplateId, PageQuery query) {
        getCouponTemplate(couponTemplateId);
        return userCouponRepository.findAllByCouponTemplateId(couponTemplateId, query);
    }

    @Transactional
    public void deleteCoupon(Long couponTemplateId) {
        CouponTemplate coupon = getCouponTemplate(couponTemplateId);
        coupon.delete();
    }

    @Transactional
    public CouponDiscount use(CouponUse couponUse) {
        if (!couponUse.hasCoupon()) {
            return CouponDiscount.none();
        }

        UserCoupon userCoupon = userCouponRepository.findById(couponUse.userCouponId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        userCoupon.checkUsableBy(couponUse.userId());

        CouponDiscountPolicy policy = couponDiscountMethod.match(userCoupon.getType());
        CouponDiscount discount = userCoupon.apply(couponUse.orderAmount(), couponUse.usedAt(), policy);

        userCoupon.use(couponUse.userId(), couponUse.usedAt());
        userCouponRepository.applyUse(userCoupon);

        return discount;
    }
}
