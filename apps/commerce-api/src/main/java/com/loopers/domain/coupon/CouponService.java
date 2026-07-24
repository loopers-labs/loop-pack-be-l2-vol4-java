package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    /** 선착순 발급 요청을 PENDING 상태로 기록한다(발행 이벤트와 같은 트랜잭션에서 호출). */
    @Transactional
    public void recordPending(String requestId, Long userId, Long couponId) {
        couponIssueRequestRepository.save(new CouponIssueRequestModel(requestId, userId, couponId));
    }

    /** 발급 요청의 현재 상태(PENDING/ISSUED/SOLD_OUT)를 조회한다. */
    @Transactional(readOnly = true)
    public String getIssueStatus(String requestId) {
        return couponIssueRequestRepository.findByRequestId(requestId)
            .map(CouponIssueRequestModel::getStatus)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
    }

    @Transactional(readOnly = true)
    public CouponModel getCoupon(Long couponId) {
        return couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional(readOnly = true)
    public List<UserCouponModel> getUserCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    @Transactional
    public UserCouponModel useCoupon(Long userId, Long userCouponId) {
        UserCouponModel userCoupon = userCouponRepository.find(userCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다."));
        if (!userCoupon.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        userCoupon.use();
        return userCoupon;
    }

    @Transactional
    public UserCouponModel issueCoupon(Long userId, Long couponId) {
        CouponModel coupon = couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        return userCouponRepository.save(new UserCouponModel(userId, couponId));
    }
}
