package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponEventPublisher couponEventPublisher;

    // 도메인 쓰기 직후 이 도메인 서비스가 사실(CouponIssueRequestedEvent)을 발행한다 - Facade는 발행하지 않는다.
    public CouponIssueRequestModel requestIssue(Long couponTemplateId, Long userId) {
        CouponIssueRequestModel request = couponIssueRequestRepository.save(
                new CouponIssueRequestModel(UUID.randomUUID().toString(), couponTemplateId, userId));
        couponEventPublisher.publish(CouponIssueRequestedEvent.from(request));
        return request;
    }

    public CouponIssueRequestModel getByRequestId(String requestId) {
        return couponIssueRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
    }

    public void complete(CouponIssueRequestModel request, Long issuedCouponId) {
        request.complete(issuedCouponId);
        couponIssueRequestRepository.save(request);
    }

    public void fail(CouponIssueRequestModel request, String reason) {
        request.fail(reason);
        couponIssueRequestRepository.save(request);
    }
}
