package com.loopers.interfaces.api.coupon;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.CouponAdminInfo;
import com.loopers.application.coupon.CouponCreateInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.application.coupon.CouponUpdateInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.utils.DateTimeUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponFacade couponFacade;
    private final DateTimeUtil dateTimeUtil;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponAdminV1Dto.CreateResponse> createCoupon(@Valid @RequestBody CouponAdminV1Dto.CreateRequest request) {
        CouponCreateInfo couponCreateInfo = couponFacade.createCoupon(
            request.name(),
            request.discountType(),
            request.discountValue(),
            request.minOrderAmount(),
            request.expiredAt(),
            dateTimeUtil.now()
        );

        return ApiResponse.success(CouponAdminV1Dto.CreateResponse.from(couponCreateInfo));
    }

    @Override
    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.UpdateResponse> updateCoupon(
        @PathVariable Long couponId,
        @Valid @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        CouponUpdateInfo couponUpdateInfo = couponFacade.updateCoupon(
            couponId,
            request.name(),
            request.discountType(),
            request.discountValue(),
            request.minOrderAmount(),
            request.expiredAt(),
            dateTimeUtil.now()
        );

        return ApiResponse.success(CouponAdminV1Dto.UpdateResponse.from(couponUpdateInfo));
    }

    @Override
    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long couponId) {
        couponFacade.deleteCoupon(couponId);

        return ApiResponse.success();
    }

    @Override
    @GetMapping
    public ApiResponse<CouponAdminV1Dto.PageResponse> readCoupons(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponAdminInfo> couponsInfo = couponFacade.readCoupons(page, size);

        return ApiResponse.success(CouponAdminV1Dto.PageResponse.from(couponsInfo));
    }

    @Override
    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.DetailResponse> readCoupon(@PathVariable Long couponId) {
        CouponAdminInfo couponAdminInfo = couponFacade.readCoupon(couponId);

        return ApiResponse.success(CouponAdminV1Dto.DetailResponse.from(couponAdminInfo));
    }

    @Override
    @GetMapping("/{couponId}/issues")
    public ApiResponse<CouponAdminV1Dto.IssuePageResponse> readCouponIssues(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponIssueInfo> issuesInfo = couponFacade.readCouponIssues(couponId, page, size, dateTimeUtil.now());

        return ApiResponse.success(CouponAdminV1Dto.IssuePageResponse.from(issuesInfo));
    }
}
