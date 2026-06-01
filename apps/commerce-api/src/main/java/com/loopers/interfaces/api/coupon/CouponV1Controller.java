package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.IssueCouponCommand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> issueCoupon(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long couponId
    ) {
        CouponIssueInfo info = couponFacade.issueCoupon(new IssueCouponCommand(userId, couponId));
        HttpStatus status = switch (info.status()) {
            case ISSUED -> HttpStatus.CREATED;
            case ALREADY_ISSUED -> HttpStatus.OK;
        };
        return ResponseEntity.status(status)
            .body(ApiResponse.success(CouponV1Dto.UserCouponResponse.from(info.coupon())));
    }
}
