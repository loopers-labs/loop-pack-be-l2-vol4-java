package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 선착순 쿠폰 발급 요청 접수. 실제 발급은 하지 않고 요청만 PENDING 으로 남긴 뒤 이벤트를 발행한다.
 * request 는 @Transactional 이라 요청 저장과 Outbox 적재(BEFORE_COMMIT)가 원자적으로 커밋된다.
 */
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponIssueRequestModel request(Long userId, Long couponId) {
        couponService.getCoupon(couponId);

        String requestId = UUID.randomUUID().toString();
        CouponIssueRequestModel request = couponIssueRequestRepository.save(
                CouponIssueRequestModel.of(requestId, couponId, userId)
        );
        eventPublisher.publishEvent(CouponIssueRequestedEvent.of(requestId, couponId, userId));
        return request;
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestModel getRequest(String requestId) {
        return couponIssueRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[requestId = " + requestId + "] 발급 요청을 찾을 수 없습니다."));
    }
}