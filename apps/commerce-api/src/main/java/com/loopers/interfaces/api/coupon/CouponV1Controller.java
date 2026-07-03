package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/{couponId}/issue-requests")
    @Override
    public ApiResponse<CouponV1Dto.IssueRequestResponse> requestIssue(
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @PathVariable Long couponId
    ) {
        CouponIssueRequestInfo info = couponFacade.requestIssue(loginId, loginPw, couponId);
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(info));
    }

    @GetMapping("/issue-requests/{requestId}")
    @Override
    public ApiResponse<CouponV1Dto.IssueRequestStatusResponse> getIssueRequestStatus(
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @PathVariable String requestId
    ) {
        CouponIssueRequestInfo info = couponFacade.getIssueRequestStatus(loginId, loginPw, requestId);
        return ApiResponse.success(CouponV1Dto.IssueRequestStatusResponse.from(info));
    }
}
