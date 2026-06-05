package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    /** 쿠폰 발급. 한 유저가 같은 템플릿을 여러 장 발급받을 수 있다. */
    @Transactional
    public IssuedCoupon issue(Long userId, Long couponTemplateId) {
        couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponTemplateId + "] 쿠폰을 찾을 수 없습니다."));
        return issuedCouponRepository.save(new IssuedCoupon(userId, couponTemplateId));
    }

    @Transactional(readOnly = true)
    public List<IssuedCoupon> getMyCoupons(Long userId) {
        return issuedCouponRepository.findAllByUserId(userId);
    }

    /** 발급 쿠폰 목록의 표시 상태(만료 판정)를 위해 템플릿을 id로 묶어 조회한다. */
    @Transactional(readOnly = true)
    public Map<Long, CouponTemplate> getTemplatesByIds(Collection<Long> ids) {
        return couponTemplateRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
    }

    /**
     * 쿠폰을 사용 처리하고 할인액을 반환한다. 소유·만료·최소금액·재사용을 검증한다.
     * 동일 쿠폰 동시 사용은 @Version 낙관적 락으로 1건만 커밋되고 나머지는 충돌 예외로 실패한다.
     * 타 유저 소유 쿠폰은 존재를 숨기기 위해 NOT_FOUND로 응답한다.
     */
    @Transactional
    public Money use(Long userId, Long couponId, Money orderAmount) {
        IssuedCoupon coupon = issuedCouponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
        if (!coupon.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다.");
        }
        CouponTemplate template = couponTemplateRepository.findById(coupon.getCouponTemplateId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 정보를 찾을 수 없습니다."));
        if (template.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
        if (!template.satisfiesMinOrderAmount(orderAmount)) {
            throw new CoreException(ErrorType.CONFLICT, "최소 주문 금액 조건을 충족하지 않습니다.");
        }
        coupon.use();
        return template.calculateDiscount(orderAmount);
    }
}
