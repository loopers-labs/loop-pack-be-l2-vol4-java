package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.MemberCoupon;
import com.loopers.coupon.domain.MemberCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 발급 쿠폰(MemberCoupon) 의 발급/조회/사용을 담당하는 application 레이어 서비스(Repository 의존). Facade 가 아니다.
 */
@RequiredArgsConstructor
@Service
public class MemberCouponService {

    private final MemberCouponRepository memberCouponRepository;
    private final CouponService couponService;

    public MemberCoupon issue(Long memberId, Long couponId) {
        Coupon coupon = couponService.get(couponId);
        return memberCouponRepository.save(
            new MemberCoupon(memberId, couponId, coupon.getExpiredAt(), ZonedDateTime.now()));
    }

    public List<MemberCoupon> getMyCoupons(Long memberId) {
        return memberCouponRepository.findByMemberId(memberId);
    }

    public List<MemberCoupon> getIssuesByCoupon(Long couponId) {
        return memberCouponRepository.findByCouponId(couponId);
    }

    /**
     * 주문에 쿠폰을 사용 처리하고 할인 금액을 반환한다.
     *
     * <p>비관적 락(findByIdForUpdate)으로 동일 쿠폰 동시 사용을 직렬화한다. 소유자/최소주문금액/사용·만료 검증을 거쳐 실패 시 예외를 던지며,
     * 호출자(OrderFacade)의 트랜잭션과 함께 롤백된다.
     */
    public long useForOrder(Long memberCouponId, Long memberId, long orderAmount, Long orderId) {
        MemberCoupon memberCoupon =
            memberCouponRepository
                .findByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        if (!memberCoupon.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }

        Coupon coupon = couponService.get(memberCoupon.getCouponId());
        long discount = coupon.calculateDiscount(orderAmount); // 최소 주문 금액 검증 포함
        memberCoupon.use(orderId, ZonedDateTime.now()); // 사용/만료 검증 + 상태 전이
        return discount;
    }
}
