package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponAdminService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponAdminService couponAdminService;

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Response.Detail> create(
            @Valid @RequestBody CouponAdminV1Request.Create request
    ) {
        return ApiResponse.success(CouponAdminV1Response.Detail.from(couponAdminService.create(request.toCommand())));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Response.Detail> get(@PathVariable Long couponId) {
        return ApiResponse.success(CouponAdminV1Response.Detail.from(couponAdminService.getCoupon(couponId)));
    }

    @GetMapping
    @Override
    public ApiResponse<CouponAdminV1Response.Page> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                CouponAdminV1Response.Page.from(couponAdminService.getCoupons(PageRequest.of(page, size)))
        );
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Response.Detail> update(
            @PathVariable Long couponId,
            @Valid @RequestBody CouponAdminV1Request.Update request
    ) {
        return ApiResponse.success(CouponAdminV1Response.Detail.from(couponAdminService.update(request.toCommand(couponId))));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponAdminService.delete(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<CouponAdminV1Response.IssuePage> getIssues(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                CouponAdminV1Response.IssuePage.from(couponAdminService.getIssues(couponId, PageRequest.of(page, size)))
        );
    }
}
