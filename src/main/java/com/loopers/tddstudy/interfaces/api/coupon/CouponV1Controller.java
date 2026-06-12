package com.loopers.tddstudy.interfaces.api.coupon;


import com.loopers.tddstudy.domain.coupon.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;


    public CouponV1Controller(UserCouponRepository userCouponRepository,
                              CouponRepository couponRepository) {
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
    }

    // 쿠폰 발급
    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<Void> issueCoupon(
            @PathVariable Long couponId,
            @RequestHeader("X-USER-ID") Long userId) {

        // 쿠폰 존재 확인
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));

        // 만료 확인
        if (coupon.isExpired()) {
            throw new IllegalArgumentException("만료된 쿠폰입니다.");
        }

        // 중복 발급 확인
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new IllegalArgumentException("이미 발급받은 쿠폰입니다.");
        }

        UserCoupon userCoupon = new UserCoupon(userId, couponId);
        userCouponRepository.save(userCoupon);

        return ResponseEntity.ok().build();
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

