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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        List<IssuedCouponModel> issued = issuedCouponService.getMyIssuedCoupons(user.getId());
        Set<Long> templateIds = issued.stream()
                .map(IssuedCouponModel::getCouponTemplateId)
                .collect(Collectors.toSet());
        Map<Long, CouponTemplateModel> templateMap = couponTemplateService.getMapByIds(templateIds);
        return issued.stream()
                .filter(i -> templateMap.containsKey(i.getCouponTemplateId()))
                .map(i -> MyIssuedCouponInfo.from(i, templateMap.get(i.getCouponTemplateId())))
                .toList();
    }

    public IssuedCouponInfo issue(String loginId, String loginPw, Long couponId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        CouponTemplateModel template = couponTemplateService.getById(couponId);
        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        IssuedCouponModel issued = issuedCouponService.issue(template.getId(), user.getId());
        return IssuedCouponInfo.from(issued);
    }
}
