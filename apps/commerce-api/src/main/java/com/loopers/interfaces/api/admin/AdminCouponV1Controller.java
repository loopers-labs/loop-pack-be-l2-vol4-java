package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.AdminCouponFacade;
import com.loopers.interfaces.api.ApiResponse;
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

import java.time.ZoneId;
import java.util.List;

/**
 * Admin 쿠폰 템플릿 API (UC-15/16). 권한 체계 미적용 — 운영 시 운영자 인가 선행 필요(브랜드·상품 관리와 동일).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller {

    private final AdminCouponFacade adminCouponFacade;

    @PostMapping
    public ApiResponse<AdminCouponV1Dto.CouponResponse> create(
        @RequestBody AdminCouponV1Dto.CreateCouponRequest request
    ) {
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(
            adminCouponFacade.register(
                request.name(),
                AdminCouponV1Dto.parseType(request.type()),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt().atZone(ZoneId.systemDefault())
            )));
    }

    @GetMapping
    public ApiResponse<List<AdminCouponV1Dto.CouponResponse>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        List<AdminCouponV1Dto.CouponResponse> responses = adminCouponFacade.getCoupons(page, size).stream()
            .map(AdminCouponV1Dto.CouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<AdminCouponV1Dto.CouponResponse> detail(
        @PathVariable(value = "couponId") Long couponId
    ) {
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(adminCouponFacade.getCoupon(couponId)));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<AdminCouponV1Dto.CouponResponse> update(
        @PathVariable(value = "couponId") Long couponId,
        @RequestBody AdminCouponV1Dto.UpdateCouponRequest request
    ) {
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(
            adminCouponFacade.update(
                couponId,
                request.name(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt().atZone(ZoneId.systemDefault())
            )));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> delete(
        @PathVariable(value = "couponId") Long couponId
    ) {
        adminCouponFacade.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<List<AdminCouponV1Dto.IssueResponse>> issues(
        @PathVariable(value = "couponId") Long couponId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        List<AdminCouponV1Dto.IssueResponse> responses = adminCouponFacade.getIssues(couponId, page, size).stream()
            .map(AdminCouponV1Dto.IssueResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
