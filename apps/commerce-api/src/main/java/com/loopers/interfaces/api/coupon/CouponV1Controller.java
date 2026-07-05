package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponCommandService;
import com.loopers.application.coupon.CouponQueryService;
import com.loopers.application.coupon.CouponResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final CouponCommandService couponCommandService;
    private final CouponQueryService couponQueryService;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.CouponIssueRequestResponse> issueCoupon(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable Long couponId
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        return ApiResponse.success(CouponV1Dto.CouponIssueRequestResponse.from(
            couponCommandService.requestIssue(couponId, loginId)
        ));
    }

    @GetMapping("/coupons/issues/{requestId}")
    public ApiResponse<CouponV1Dto.CouponIssueRequestResponse> getIssueRequest(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable Long requestId
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        return ApiResponse.success(CouponV1Dto.CouponIssueRequestResponse.from(
            couponQueryService.getMyIssueRequest(requestId, loginId)
        ));
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<PageResponse<CouponV1Dto.IssuedCouponResponse>> getMyCoupons(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        PageResult<CouponResult.Issued> result = couponQueryService.getMyCoupons(loginId, page, size);
        return ApiResponse.success(PageResponse.from(result, CouponV1Dto.IssuedCouponResponse::from));
    }
}
