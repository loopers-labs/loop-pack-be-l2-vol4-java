package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponService couponService;

    /** FR-CA-01. 쿠폰 템플릿 목록 조회 */
    @GetMapping
    public ApiResponse<Page<CouponV1Dto.CouponResponse>> getCoupons(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(couponService.getAll(pageable).map(CouponV1Dto.CouponResponse::from));
    }

    /** FR-CA-02. 쿠폰 템플릿 상세 조회 */
    @GetMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        return ApiResponse.success(CouponV1Dto.CouponResponse.from(couponService.getById(couponId)));
    }

    /** FR-CA-03. 쿠폰 템플릿 등록 */
    @PostMapping
    public ApiResponse<CouponV1Dto.CouponResponse> createCoupon(
        @Valid @RequestBody CouponV1Dto.CouponCreateRequest request
    ) {
        return ApiResponse.success(CouponV1Dto.CouponResponse.from(couponService.create(request.toCommand())));
    }

    /** FR-CA-04. 쿠폰 템플릿 수정 */
    @PutMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.CouponResponse> updateCoupon(
        @PathVariable Long couponId,
        @Valid @RequestBody CouponV1Dto.CouponUpdateRequest request
    ) {
        return ApiResponse.success(CouponV1Dto.CouponResponse.from(couponService.update(couponId, request.toCommand())));
    }

    /** FR-CA-05. 쿠폰 템플릿 삭제 */
    @DeleteMapping("/{couponId}")
    public ApiResponse<Object> deleteCoupon(@PathVariable Long couponId) {
        couponService.delete(couponId);
        return ApiResponse.success();
    }

    /** FR-CA-06. 특정 쿠폰 발급 내역 조회 */
    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<CouponV1Dto.UserCouponResponse>> getIssues(
        @PathVariable Long couponId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(couponService.getIssues(couponId, pageable).map(CouponV1Dto.UserCouponResponse::from));
    }
}
