package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.CouponDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller {

    private final CouponFacade couponFacade;

    @GetMapping
    public ApiResponse<List<CouponDto.Template.Response>> getCoupons(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<CouponDto.Template.Response> responses = couponFacade.getCoupons(page, size).stream()
            .map(CouponDto.Template.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponDto.Template.Response> getCoupon(
        @PathVariable(value = "couponId") Long couponId
    ) {
        CouponInfo.Template info = couponFacade.getCoupon(couponId);
        return ApiResponse.success(CouponDto.Template.Response.from(info));
    }

    @PostMapping
    public ApiResponse<CouponDto.Create.V1.Response> createCoupon(
        @Valid @RequestBody CouponDto.Create.V1.Request request
    ) {
        CouponInfo.Template info = couponFacade.createCoupon(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt()
        );
        return ApiResponse.success(CouponDto.Create.V1.Response.from(info));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponDto.Update.V1.Response> updateCoupon(
        @PathVariable(value = "couponId") Long couponId,
        @Valid @RequestBody CouponDto.Update.V1.Request request
    ) {
        CouponInfo.Template info = couponFacade.updateCoupon(
            couponId,
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt()
        );
        return ApiResponse.success(CouponDto.Update.V1.Response.from(info));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(
        @PathVariable(value = "couponId") Long couponId
    ) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<List<CouponDto.Issued.Response>> getIssuedCoupons(
        @PathVariable(value = "couponId") Long couponId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<CouponDto.Issued.Response> responses = couponFacade.getIssuedCoupons(couponId, page, size, ZonedDateTime.now()).stream()
            .map(CouponDto.Issued.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
