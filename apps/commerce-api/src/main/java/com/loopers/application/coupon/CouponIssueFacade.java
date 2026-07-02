package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.event.CouponIssueRequested;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponIssueFacade {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final CouponIssueResultRepository resultRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public String requestIssue(String loginId, Long couponId) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        Coupon coupon = couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (coupon.getQuantity() == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰이 아닙니다.");
        }
        if (!coupon.getExpiredAt().isAfter(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        String requestId = UUID.randomUUID().toString();
        resultRepository.save(CouponIssueResult.pending(requestId, couponId, user.getId()));
        eventPublisher.publishEvent(new CouponIssueRequested(requestId, couponId, user.getId(), ZonedDateTime.now()));
        return requestId;
    }

    @Transactional(readOnly = true)
    public CouponIssueResultInfo getResult(String requestId) {
        return resultRepository.findByRequestId(requestId)
            .map(CouponIssueResultInfo::from)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
    }
}
