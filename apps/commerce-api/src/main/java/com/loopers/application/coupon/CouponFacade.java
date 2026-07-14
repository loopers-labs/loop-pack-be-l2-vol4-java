package com.loopers.application.coupon;

import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponInfo.Template createCoupon(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        return CouponInfo.Template.from(couponService.createCoupon(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional
    public CouponInfo.Template createFirstComeCoupon(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        Long issueLimit
    ) {
        return CouponInfo.Template.from(couponService.createFirstComeCoupon(name, type, value, minOrderAmount, expiredAt, issueLimit));
    }

    @Transactional(readOnly = true)
    public CouponInfo.Template getCoupon(Long id) {
        return CouponInfo.Template.from(couponService.getCoupon(id));
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.Template> getCoupons(Integer page, Integer size) {
        return couponService.getCoupons(page, size).stream()
            .map(CouponInfo.Template::from)
            .toList();
    }

    @Transactional
    public CouponInfo.Template updateCoupon(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        return CouponInfo.Template.from(couponService.updateCoupon(id, name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponService.deleteCoupon(id);
    }

    @Transactional
    public CouponInfo.Issued issueCoupon(Long couponId, String userLoginId, ZonedDateTime now) {
        return CouponInfo.Issued.from(couponService.issueCoupon(couponId, userLoginId, now), now);
    }

    @Transactional
    public CouponInfo.FirstComeIssueRequest requestFirstComeIssue(Long couponId, String userLoginId, ZonedDateTime now) {
        Coupon coupon = couponService.getCoupon(couponId);
        if (!coupon.isFirstCome()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰만 비동기 발급 요청을 처리할 수 있습니다.");
        }
        if (coupon.isExpired(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        CouponIssueRequest request = couponIssueRequestRepository.save(CouponIssueRequest.request(couponId, userLoginId, now));
        eventPublisher.publishEvent(CouponIssueRequestedEvent.of(couponId, userLoginId, request.getRequestId()));
        return CouponInfo.FirstComeIssueRequest.from(request);
    }

    @Transactional(readOnly = true)
    public CouponInfo.FirstComeIssueResult getFirstComeIssueResult(String requestId, String userLoginId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[requestId = " + requestId + "] 쿠폰 발급 요청을 찾을 수 없습니다."));
        if (!request.getUserLoginId().equals(userLoginId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[requestId = " + requestId + "] 쿠폰 발급 요청을 찾을 수 없습니다.");
        }
        return CouponInfo.FirstComeIssueResult.from(request);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.Issued> getMyCoupons(String userLoginId, ZonedDateTime now) {
        return couponService.getIssuedCoupons(userLoginId).stream()
            .map(issuedCoupon -> CouponInfo.Issued.from(issuedCoupon, now))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.Issued> getIssuedCoupons(Long couponId, Integer page, Integer size, ZonedDateTime now) {
        return couponService.getIssuedCoupons(couponId, page, size).stream()
            .map(issuedCoupon -> CouponInfo.Issued.from(issuedCoupon, now))
            .toList();
    }
}
