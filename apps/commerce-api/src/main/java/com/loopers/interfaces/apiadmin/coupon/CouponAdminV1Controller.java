package com.loopers.interfaces.apiadmin.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponFacade couponFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> getList(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(PageResponse.from(CouponAdminV1Dto.CouponResponse.from(couponFacade.getList(pageable))));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(couponFacade.get(couponId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> register(
            @Valid @RequestBody CouponAdminV1Dto.RegisterRequest request
    ) {
        return ApiResponse.success(
                CouponAdminV1Dto.CouponResponse.from(
                        couponFacade.create(request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt())
                )
        );
    }

    @PatchMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> update(
            @PathVariable Long couponId,
            @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        return ApiResponse.success(
                CouponAdminV1Dto.CouponResponse.from(
                        couponFacade.update(couponId, request.name(), request.expiredAt())
                )
        );
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponFacade.delete(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>> getIssues(
            @PathVariable Long couponId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(PageResponse.from(CouponAdminV1Dto.IssueResponse.from(couponFacade.getIssues(couponId, pageable))));
    }
}
