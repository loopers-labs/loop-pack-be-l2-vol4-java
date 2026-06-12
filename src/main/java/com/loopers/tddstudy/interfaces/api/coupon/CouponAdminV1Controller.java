package com.loopers.tddstudy.interfaces.api.coupon;


import com.loopers.tddstudy.domain.coupon.Coupon;
import com.loopers.tddstudy.domain.coupon.CouponRepository;
import com.loopers.tddstudy.domain.coupon.CouponType;
import com.loopers.tddstudy.domain.coupon.UserCouponRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponAdminV1Controller(CouponRepository couponRepository,
                                   UserCouponRepository userCouponRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    // 쿠폰 템플릿 목록 조회
    @GetMapping
    public ResponseEntity<List<CouponAdminV1Dto.CouponResponse>> getCoupons() {
        List<CouponAdminV1Dto.CouponResponse> result = couponRepository.findAll().stream()
                .map(c -> new CouponAdminV1Dto.CouponResponse(
                        c.getId(), c.getName(), c.getType().name(),
                        c.getValue(), c.getMinOrderAmount(), c.getExpiredAt()))
                .toList();
        return ResponseEntity.ok(result);
    }

    // 쿠폰 템플릿 상세 조회
    @GetMapping("/{couponId}")
    public ResponseEntity<CouponAdminV1Dto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));
        return ResponseEntity.ok(new CouponAdminV1Dto.CouponResponse(
                coupon.getId(), coupon.getName(), coupon.getType().name(),
                coupon.getValue(), coupon.getMinOrderAmount(), coupon.getExpiredAt()));
    }

    // 쿠폰 템플릿 등록
    @PostMapping
    public ResponseEntity<CouponAdminV1Dto.CouponResponse> createCoupon(
            @RequestBody CouponAdminV1Dto.CreateCouponRequest request) {
        Coupon coupon = new Coupon(
                request.name(),
                CouponType.valueOf(request.type()),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt()
        );
        Coupon saved = couponRepository.save(coupon);
        return ResponseEntity.ok(new CouponAdminV1Dto.CouponResponse(
                saved.getId(), saved.getName(), saved.getType().name(),
                saved.getValue(), saved.getMinOrderAmount(), saved.getExpiredAt()));
    }


    // 쿠폰 템플릿 수정
    @PutMapping("/{couponId}")
    public ResponseEntity<CouponAdminV1Dto.CouponResponse> updateCoupon(
            @PathVariable Long couponId,
            @RequestBody CouponAdminV1Dto.UpdateCouponRequest request) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));
        coupon.update(request.name(), request.value(), request.minOrderAmount(), request.expiredAt());
        Coupon saved = couponRepository.save(coupon);
        return ResponseEntity.ok(new CouponAdminV1Dto.CouponResponse(
                saved.getId(), saved.getName(), saved.getType().name(),
                saved.getValue(), saved.getMinOrderAmount(), saved.getExpiredAt()));
    }

    // 쿠폰 템플릿 삭제
    @DeleteMapping("/{couponId}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long couponId) {
        couponRepository.deleteById(couponId);
        return ResponseEntity.ok().build();
    }

    // 발급 내역 조회
    @GetMapping("/{couponId}/issues")
    public ResponseEntity<List<CouponAdminV1Dto.CouponIssueResponse>> getIssues(
            @PathVariable Long couponId) {
        List<CouponAdminV1Dto.CouponIssueResponse> result =
                userCouponRepository.findAllByCouponId(couponId).stream()
                        .map(uc -> new CouponAdminV1Dto.CouponIssueResponse(
                                uc.getId(), uc.getUserId(),
                                uc.getStatus().name(), uc.getIssuedAt(), uc.getUsedAt()))
                        .toList();
        return ResponseEntity.ok(result);
    }


}
