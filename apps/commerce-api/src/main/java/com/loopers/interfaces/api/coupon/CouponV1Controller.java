package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.application.coupon.UserCouponService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.CurrentUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final UserCouponService userCouponService;
    private final CouponIssueFacade couponIssueFacade;

    /**
     * FR-C-01. 쿠폰 발급 요청 (비동기) — Kafka로 발행만 하고 즉시 반환한다.
     * 실제 발급은 Consumer가 처리하며, 결과는 GET /coupons/issue-requests/{requestId}로 폴링해 확인한다.
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.IssueRequestResponse> issueCoupon(
        @CurrentUser UserModel currentUser,
        @Positive @PathVariable Long couponId
    ) {
        return ApiResponse.success(
            CouponV1Dto.IssueRequestResponse.from(
                couponIssueFacade.requestIssue(currentUser.getId(), couponId)
            )
        );
    }

    /** FR-C-03. 쿠폰 발급 요청 결과 조회 (폴링) */
    @GetMapping("/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponV1Dto.IssueRequestResponse> getIssueRequest(
        @CurrentUser UserModel currentUser,
        @Positive @PathVariable Long requestId
    ) {
        return ApiResponse.success(
            CouponV1Dto.IssueRequestResponse.from(
                couponIssueFacade.getRequest(requestId, currentUser.getId())
            )
        );
    }

    /** FR-C-02. 내 쿠폰 목록 조회 */
    @GetMapping("/users/me/coupons")
    public ApiResponse<Page<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @CurrentUser UserModel currentUser,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
            userCouponService.getMyCoupons(currentUser.getId(), pageable)
                .map(CouponV1Dto.UserCouponResponse::from)
        );
    }
}
