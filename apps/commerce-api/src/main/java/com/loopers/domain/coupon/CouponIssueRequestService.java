package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestService {

    private final CouponIssueRequestRepository repository;

    @Transactional
    public CouponIssueRequest create(String requestId, Long userId, Long couponTemplateId) {
        return repository.save(new CouponIssueRequest(requestId, userId, couponTemplateId));
    }

    @Transactional(readOnly = true)
    public CouponIssueRequest getByRequestId(String requestId) {
        return repository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[requestId = " + requestId + "] 발급 요청을 찾을 수 없습니다."));
    }

    @Transactional
    public void save(CouponIssueRequest request) {
        repository.save(request);
    }
}
