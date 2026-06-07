package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponType;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponAdminV1Dto {

    public record CreateRequest(
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record UpdateRequest(
        String name,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record Response(
        Long id,
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime deletedAt
    ) {
        public static Response from(CouponAdminInfo info) {
            return new Response(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.deletedAt()
            );
        }
    }

    public record PageResponse(
        List<Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static PageResponse from(Page<CouponAdminInfo> page) {
            List<Response> content = page.getContent().stream()
                .map(Response::from)
                .toList();
            return new PageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }

    public record IssuedCouponResponse(
        Long id,
        Long userId,
        Long couponPolicyId,
        CouponType type,
        long discountValue,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponDisplayStatus status,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                info.id(),
                info.userId(),
                info.couponPolicyId(),
                info.type(),
                info.discountValue(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.status(),
                info.usedAt(),
                info.issuedAt()
            );
        }
    }

    public record IssuedCouponPageResponse(
        List<IssuedCouponResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static IssuedCouponPageResponse from(Page<IssuedCouponInfo> page) {
            List<IssuedCouponResponse> content = page.getContent().stream()
                .map(IssuedCouponResponse::from)
                .toList();
            return new IssuedCouponPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
