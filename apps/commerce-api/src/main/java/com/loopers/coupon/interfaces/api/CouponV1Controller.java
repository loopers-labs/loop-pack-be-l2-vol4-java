package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponFacade;
import com.loopers.coupon.application.IssuedCouponInfo;
import com.loopers.coupon.application.IssueCouponCommand;
import com.loopers.coupon.domain.CouponIssueStatus;
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
        @PathVariable("couponId") Long couponTemplateId
    ) {
        IssuedCouponInfo issuedCoupon = couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplateId));
        HttpStatus status = issuedCoupon.status() == CouponIssueStatus.ISSUED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(ApiResponse.success(CouponV1Dto.UserCouponResponse.from(issuedCoupon.coupon())));
    }
}
