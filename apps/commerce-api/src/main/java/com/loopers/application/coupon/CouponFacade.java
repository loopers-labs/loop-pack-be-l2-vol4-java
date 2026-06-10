package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final IssuedCouponService issuedCouponService;
    private final UserService userService;

    public Page<IssuedCouponInfo> getIssuedCouponsByTemplateId(Long couponTemplateId, Pageable pageable) {
        CouponTemplateModel template = couponTemplateService.getById(couponTemplateId);
        return issuedCouponService.findAllByCouponTemplateId(template.getId(), pageable)
                .map(IssuedCouponInfo::from);
    }

    public List<MyIssuedCouponInfo> getMyIssuedCoupons(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        return issuedCouponService.getMyIssuedCoupons(user.getId()).stream()
                .map(issued -> {
                    CouponTemplateModel template = couponTemplateService.getById(issued.getCouponTemplateId());
                    return MyIssuedCouponInfo.from(issued, template);
                })
                .toList();
    }

    public IssuedCouponInfo issue(String loginId, String loginPw, Long couponId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        CouponTemplateModel template = couponTemplateService.getById(couponId);
        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰 템플릿입니다.");
        }
        IssuedCouponModel issued = issuedCouponService.issue(template.getId(), user.getId());
        return IssuedCouponInfo.from(issued);
    }
}
