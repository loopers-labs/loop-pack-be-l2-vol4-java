package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping
    @Override
    public ApiResponse<CouponV1Dto.TemplateResponse> create(@RequestBody @Valid CouponV1Dto.CreateRequest request) {
        CouponInfo info = couponFacade.create(request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt());
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(info));
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<CouponV1Dto.TemplateResponse> get(@PathVariable UUID id) {
        CouponInfo info = couponFacade.get(id);
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<CouponV1Dto.TemplateResponse>> getList(Pageable pageable) {
        Page<CouponInfo> page = couponFacade.getList(pageable);
        return ApiResponse.success(PageResponse.from(page.map(CouponV1Dto.TemplateResponse::from)));
    }

    @PutMapping("/{id}")
    @Override
    public ApiResponse<CouponV1Dto.TemplateResponse> update(
        @PathVariable UUID id,
        @RequestBody @Valid CouponV1Dto.UpdateRequest request
    ) {
        CouponInfo info = couponFacade.update(id, request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt());
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(info));
    }

    @DeleteMapping("/{id}")
    @Override
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        couponFacade.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/issues")
    @Override
    public ApiResponse<PageResponse<CouponV1Dto.UserCouponResponse>> getIssues(@PathVariable UUID id, Pageable pageable) {
        Page<UserCouponInfo> page = couponFacade.getIssuesByTemplate(id, pageable);
        return ApiResponse.success(PageResponse.from(page.map(CouponV1Dto.UserCouponResponse::from)));
    }
}
