package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
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

    /** 소유권 검증 — 본인 쿠폰 아니면 NOT_FOUND */
    public UserCouponModel getOwned(UUID couponId, UUID userId) {
        return userCouponRepository.findByIdAndUserId(couponId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "보유한 쿠폰을 찾을 수 없습니다."));
    }

    /** 쿠폰 사용 — 조건부 UPDATE. 이미 사용됐으면(affected=0) CONFLICT */
    @Transactional
    public void use(UUID couponId, UUID orderId) {
        int affected = userCouponRepository.useIfAvailable(couponId, orderId, ZonedDateTime.now());
        if (affected == 0) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
    }

    /** 사용 취소(결제 실패/만료) — USED → AVAILABLE. 멱등 */
    @Transactional
    public void releaseByOrderId(UUID orderId) {
        userCouponRepository.releaseByOrderId(orderId);
    }

    @Transactional
    public void releaseByOrderIds(List<UUID> orderIds) {
        if (orderIds.isEmpty()) return;
        userCouponRepository.releaseByOrderIds(orderIds);
    }
}
