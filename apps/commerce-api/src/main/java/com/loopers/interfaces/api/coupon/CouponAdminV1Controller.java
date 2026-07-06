package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

/**
 * 어드민 쿠폰 템플릿 API.
 *
 * <p>요청의 {@code expiredAt} 은 오프셋 없는 LocalDateTime 으로 받아 시스템 기준 시간대로 변환한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponApplicationService couponApplicationService;

    @GetMapping
    public ApiResponse<List<CouponAdminV1Dto.CouponResponse>> getCoupons(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<CouponAdminV1Dto.CouponResponse> responses = couponApplicationService.getTemplates(page, size).stream()
            .map(CouponAdminV1Dto.CouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId
    ) {
        CouponInfo info = couponApplicationService.getTemplate(couponId);
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @Valid @RequestBody CouponAdminV1Dto.CreateCouponRequest request
    ) {
        CouponInfo info = couponApplicationService.createTemplate(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt().atZone(ZoneId.of("Asia/Seoul")),
            request.totalQuantity()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId,
        @Valid @RequestBody CouponAdminV1Dto.UpdateCouponRequest request
    ) {
        CouponInfo info = couponApplicationService.updateTemplate(
            couponId,
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt().atZone(ZoneId.of("Asia/Seoul")),
            request.totalQuantity()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId
    ) {
        couponApplicationService.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<List<CouponAdminV1Dto.IssueResponse>> getIssues(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<CouponAdminV1Dto.IssueResponse> responses = couponApplicationService.getIssues(couponId, page, size).stream()
            .map(CouponAdminV1Dto.IssueResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
