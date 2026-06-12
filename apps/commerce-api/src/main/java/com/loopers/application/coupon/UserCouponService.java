package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    @Transactional
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponRepository.save(userCoupon);
    }

    @Transactional(readOnly = true)
    public UserCouponModel getById(Long id) {
        return userCouponRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[userCouponId = " + id + "] 유저 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long memberId) {
        return userCouponRepository.findAllByMemberId(memberId).stream()
            .map(uc -> {
                var template = couponTemplateRepository.findById(uc.getTemplateId()).orElseThrow();
                return new UserCouponInfo(uc.getId(), template.getName(), template.getExpiredAt(), uc.getStatus(template.getExpiredAt(), template.isBlocked()));
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<UserCouponModel> getIssuances(Long templateId, PageRequest pageRequest) {
        return userCouponRepository.findAllByTemplateId(templateId, pageRequest);
    }
}
