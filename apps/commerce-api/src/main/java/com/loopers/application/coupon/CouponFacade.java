package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/** 대고객 쿠폰 유스케이스 — 동기 발급(UC-13), 내 쿠폰 목록(UC-14), 선착순 비동기 발급 요청(Slice 4). */
@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final UserCouponService userCouponService;
    private final CouponService couponService;
    private final CouponIssueRequestService couponIssueRequestService;

    public IssuedCouponInfo issue(Long userId, Long couponId) {
        return IssuedCouponInfo.from(userCouponService.issue(userId, couponId));
    }

    public List<IssuedCouponInfo> getMyCoupons(Long userId, int page, int size) {
        return userCouponService.getMyCoupons(userId, page, size).stream()
                .map(IssuedCouponInfo::from)
                .toList();
    }

    /**
     * 선착순 발급 요청 접수(Slice 4). 쿠폰 사전검증(존재/활성/만료) 후 요청을 PENDING으로 남기고 즉시 반환한다.
     * 실제 발급(수량 확보)은 commerce-streamer 컨슈머가 비동기로 확정 → 결과는 {@link #getIssueResult}로 조회.
     */
    public CouponIssueRequestInfo requestIssue(Long userId, Long couponId) {
        couponService.getIssuableTemplate(couponId, ZonedDateTime.now()); // 미존재 NOT_FOUND / 비활성·만료 BAD_REQUEST
        return CouponIssueRequestInfo.from(couponIssueRequestService.request(userId, couponId));
    }

    public CouponIssueRequestInfo getIssueResult(Long requestId) {
        return CouponIssueRequestInfo.from(couponIssueRequestService.getResult(requestId));
    }
}
