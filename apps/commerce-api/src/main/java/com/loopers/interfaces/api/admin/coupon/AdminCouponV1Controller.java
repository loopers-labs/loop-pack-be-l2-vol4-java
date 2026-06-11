package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/coupons")
public class AdminCouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping
    public ApiResponse<AdminCouponV1Dto.CouponResponse> createCoupon(
            @RequestBody AdminCouponV1Dto.CreateCouponRequest request
    ) {
        CouponInfo info = couponFacade.createCoupon(
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt(),
                request.quantity()
        );
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(info));
    }

    @GetMapping
    public ApiResponse<Page<AdminCouponV1Dto.CouponResponse>> getCoupons(
            @PageableDefault(size = 20) final Pageable pageable
    ) {
        Page<AdminCouponV1Dto.CouponResponse> response = couponFacade.getCoupons(pageable)
                .map(AdminCouponV1Dto.CouponResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<AdminCouponV1Dto.CouponResponse> getCoupon(
            @PathVariable(value = "couponId") Long couponId
    ) {
        CouponInfo info = couponFacade.getCoupon(couponId);
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(info));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<AdminCouponV1Dto.CouponResponse> updateCoupon(
            @PathVariable(value = "couponId") Long couponId,
            @RequestBody AdminCouponV1Dto.UpdateCouponRequest request
    ) {
        CouponInfo info = couponFacade.updateCoupon(
                couponId,
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt()
        );
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(
            @PathVariable(value = "couponId") Long couponId
    ) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    // 특정 쿠폰 발급 내역
    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<AdminCouponV1Dto.CouponIssueResponse>> getCouponIssues(
            @PathVariable(value = "couponId") Long couponId,
            @PageableDefault(size = 20) final Pageable pageable
    ) {
        Page<CouponIssueInfo> issues = couponFacade.getCouponIssues(couponId, pageable);
        Page<AdminCouponV1Dto.CouponIssueResponse> response = issues.map(AdminCouponV1Dto.CouponIssueResponse::from);
        return ApiResponse.success(response);
    }
}
