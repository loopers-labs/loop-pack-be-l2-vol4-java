package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestService;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponService;
import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// coupon-issue-requests 컨슈머의 실제 발급 처리. event_handled 선점과 발급 반영을 하나의 트랜잭션으로 묶어
// 선점 성공 후 발급 중 실패해도 선점 자체가 롤백되어(멱등) 재전달 시 다시 온전히 처리될 수 있게 한다.
@RequiredArgsConstructor
@Component
public class CouponIssueProcessor {

    private static final String QUANTITY_EXCEEDED_REASON = "발급 가능 수량을 초과했습니다.";

    private final EventHandledService eventHandledService;
    private final CouponIssueRequestService couponIssueRequestService;
    private final CouponTemplateService couponTemplateService;
    private final IssuedCouponService issuedCouponService;

    @Transactional
    public void process(String eventId, String requestId, Long couponTemplateId, Long userId) {
        if (!eventHandledService.markHandled(eventId)) {
            return;
        }
        CouponIssueRequestModel request = couponIssueRequestService.getByRequestId(requestId);
        if (!couponTemplateService.reserveQuantity(couponTemplateId)) {
            couponIssueRequestService.fail(request, QUANTITY_EXCEEDED_REASON);
            return;
        }
        try {
            IssuedCouponModel issued = issuedCouponService.issue(couponTemplateId, userId);
            couponIssueRequestService.complete(request, issued.getId());
        } catch (CoreException e) {
            // 요청 단계의 (couponTemplateId, userId) UNIQUE가 중복 요청 자체를 막기 때문에 실제로는 거의 발생하지 않는다.
            // 수량 선점을 되돌리는 보상 로직 없이 실패로만 기록한다 - 이 경로가 반복 발생하면 상위 원인(중복 요청 유입)을 봐야 한다.
            couponIssueRequestService.fail(request, e.getMessage());
        }
    }
}
