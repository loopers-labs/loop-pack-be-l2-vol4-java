package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;

    /** 템플릿 스냅샷을 복사해 발급. 동일 템플릿 중복 발급 시 CONFLICT. */
    public UserCouponModel issue(UUID userId, CouponTemplateModel template) {
        if (userCouponRepository.existsByUserIdAndTemplateId(userId, template.getId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
        UserCouponModel userCoupon = new UserCouponModel(
            userId, template.getId(), template.getType(), template.getValue(),
            template.getMinOrderAmount(), template.getExpiredAt());
        return userCouponRepository.save(userCoupon);
    }

    public List<UserCouponModel> getMyCoupons(UUID userId) {
        return userCouponRepository.findByUserId(userId);
    }

    public Page<UserCouponModel> getIssuesByTemplate(UUID templateId, Pageable pageable) {
        return userCouponRepository.findByTemplateId(templateId, pageable);
    }
}
