package com.loopers.tddstudy.interfaces.api.coupon;


import com.loopers.tddstudy.domain.coupon.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.loopers.tddstudy.application.coupon.CouponRequestService;

@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final CouponRequestService couponRequestService;


    public CouponV1Controller(UserCouponRepository userCouponRepository,
                              CouponRepository couponRepository,
                              CouponRequestService couponRequestService) {   // ← 추가
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
        this.couponRequestService = couponRequestService;                    // ← 추가
    }

    // 발급 "요청"만 (실제 발급은 Consumer가)
    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<CouponV1Dto.IssueAcceptedResponse> issueCoupon(
            @PathVariable Long couponId,
            @RequestHeader("X-USER-ID") Long userId) {
        String requestId = couponRequestService.request(couponId, userId);
        return ResponseEntity.accepted()
                .body(new CouponV1Dto.IssueAcceptedResponse(requestId, "PENDING"));
    }

    // 발급 결과 폴링
    @GetMapping("/coupons/issue-result/{requestId}")
    public ResponseEntity<CouponV1Dto.IssueResultResponse> issueResult(
            @PathVariable String requestId) {
        var r = couponRequestService.getResult(requestId);
        return ResponseEntity.ok(
                new CouponV1Dto.IssueResultResponse(r.getRequestId(), r.getStatus(), r.getReason()));
    }

    // 내 쿠폰 목록
    @GetMapping("/users/me/coupons")
    public ResponseEntity<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
            @RequestHeader("X-USER-ID") Long userId) {

        List<UserCoupon> userCoupons = userCouponRepository.findAllByUserId(userId);

        List<CouponV1Dto.UserCouponResponse> result = userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow();

                    // 만료 여부 동적 계산
                    String status = uc.getStatus() == UserCouponStatus.AVAILABLE && coupon.isExpired()
                            ? "EXPIRED"
                            : uc.getStatus().name();

                    return new CouponV1Dto.UserCouponResponse(
                            uc.getId(),
                            coupon.getId(),
                            coupon.getName(),
                            coupon.getType().name(),
                            coupon.getValue(),
                            status
                    );
                })
                .toList();

        return ResponseEntity.ok(result);
    }

//
}

