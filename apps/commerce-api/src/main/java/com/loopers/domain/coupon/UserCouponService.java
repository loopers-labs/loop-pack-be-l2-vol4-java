package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 쿠폰 발급/조회/사용 처리.
 * <p>
 * 사용 시점에는 비관적 락(findForUpdate) 으로 동시 사용을 차단한다.
 * 사용 자체는 도메인 메서드 UserCoupon.use 가 담당하며, 본 서비스는 락 + 검증 + 저장 흐름을 조립한다.
 */
@RequiredArgsConstructor
@Component
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponTemplateService couponTemplateService;

    @Transactional
    public UserCoupon issue(Long userId, Long couponTemplateId) {
        couponTemplateService.get(couponTemplateId); // 존재 검증
        UserCoupon issued = new UserCoupon(userId, couponTemplateId, LocalDateTime.now());
        return userCouponRepository.save(issued);
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getMyCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getIssues(Long couponTemplateId, int page, int size) {
        return userCouponRepository.findAllByCouponTemplateId(couponTemplateId, page, size);
    }

    /**
     * 주문 사용 시점에 쿠폰을 잠그고 USED 로 전이시킨다.
     * <ol>
     *   <li>비관적 락으로 본 행을 점유 — 같은 쿠폰을 노리는 다른 트랜잭션은 대기</li>
     *   <li>소유자/상태/만료 검증</li>
     *   <li>도메인 메서드 use 로 상태 전이 (이미 사용된 쿠폰이면 CONFLICT)</li>
     * </ol>
     */
    @Transactional
    public AppliedCoupon useForOrder(Long userId, Long userCouponId, long orderAmount) {
        UserCoupon coupon = userCouponRepository.findForUpdate(userCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + userCouponId + "] 쿠폰을 찾을 수 없습니다."));

        coupon.assertOwnedBy(userId);

        CouponTemplate template = couponTemplateService.get(coupon.getCouponTemplateId());
        LocalDateTime now = LocalDateTime.now();
        if (template.isExpiredAt(now)) {
            coupon.expire();
            userCouponRepository.save(coupon);
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
        if (!template.isApplicable(orderAmount)) {
            throw new CoreException(ErrorType.CONFLICT,
                "최소 주문 금액 " + template.getMinOrderAmount() + " 원 미만이라 쿠폰을 사용할 수 없습니다.");
        }

        long discount = template.discountFor(orderAmount);
        coupon.use(now); // CONFLICT 가능 — 이미 USED/EXPIRED
        userCouponRepository.save(coupon);

        return new AppliedCoupon(coupon.getId(), discount);
    }

    public record AppliedCoupon(Long userCouponId, long discountAmount) {}
}
