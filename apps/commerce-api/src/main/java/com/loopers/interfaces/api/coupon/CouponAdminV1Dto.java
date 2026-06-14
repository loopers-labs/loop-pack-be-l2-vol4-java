package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.application.support.PageResult;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponAdminV1Dto {

    public record CreateCouponRequest(
        String name,
        CouponType type,
        long value,
        BigDecimal minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record UpdateCouponRequest(
        String name,
        CouponType type,
        long value,
        BigDecimal minOrderAmount,
        LocalDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        long value,
        BigDecimal minOrderAmount,
        LocalDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt()
            );
        }
    }

    public record CouponPageResponse(
        List<CouponResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static CouponPageResponse from(PageResult<CouponInfo> result) {
            return new CouponPageResponse(
                result.items().stream().map(CouponResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
            );
        }
    }

    public record IssueResponse(
        Long id,
        Long userId,
        CouponStatus status,
        ZonedDateTime issuedAt
    ) {
        public static IssueResponse from(CouponIssueInfo info) {
            return new IssueResponse(info.id(), info.userId(), info.status(), info.issuedAt());
        }
    }

    public record IssuePageResponse(
        List<IssueResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static IssuePageResponse from(PageResult<CouponIssueInfo> result) {
            return new IssuePageResponse(
                result.items().stream().map(IssueResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
            );
        }
    }
}
