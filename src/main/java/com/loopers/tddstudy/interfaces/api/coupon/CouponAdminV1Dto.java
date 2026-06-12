package com.loopers.tddstudy.interfaces.api.coupon;

import java.time.LocalDateTime;

public class CouponAdminV1Dto {

    // 쿠폰 템플릿 생성 요청
    public record CreateCouponRequest(
            String name,
            String type,           // FIXED | RATE
            int value,
            int minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    // 쿠폰 템플릿 수정 요청
    public record UpdateCouponRequest(
            String name,
            int value,
            int minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    // 쿠폰 템플릿 응답
    public record CouponResponse(
            Long id,
            String name,
            String type,
            int value,
            int minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    // 발급 내역 응답
    public record CouponIssueResponse(
            Long userCouponId,
            Long userId,
            String status,
            LocalDateTime issuedAt,
            LocalDateTime usedAt
    ) {}




}
