package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.DiscountType;

import java.time.LocalDateTime;
import java.util.List;

public final class CouponV1Dto {

    private CouponV1Dto() {
    }

    // ---------- 어드민 요청 ----------

    public record RegisterRequest(String name, DiscountType discountType, long discountValue,
                                  long minOrderAmount, int validDays) {
    }

    public record ModifyRequest(String name, DiscountType discountType, long discountValue,
                                long minOrderAmount, int validDays) {
    }

    // ---------- 어드민 응답 ----------

    public record TemplateResponse(
            Long id,
            String name,
            DiscountType discountType,
            long discountValue,
            long minOrderAmount,
            int validDays
    ) {

        public static TemplateResponse from(CouponInfo.Template info) {
            return new TemplateResponse(info.id(), info.name(), info.discountType(), info.discountValue(),
                    info.minOrderAmount(), info.validDays());
        }
    }

    public record TemplatePageResponse(
            List<TemplateResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static TemplatePageResponse from(PageResult<CouponInfo.Template> result) {
            return new TemplatePageResponse(
                    result.content().stream().map(TemplateResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }

    public record IssueHistoryResponse(
            Long id,
            Long userId,
            CouponStatus status,
            LocalDateTime expiresAt,
            LocalDateTime usedAt
    ) {

        public static IssueHistoryResponse from(CouponInfo.IssueHistoryItem info) {
            return new IssueHistoryResponse(info.id(), info.userId(), info.status(), info.expiresAt(), info.usedAt());
        }
    }

    public record IssueHistoryPageResponse(
            List<IssueHistoryResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static IssueHistoryPageResponse from(PageResult<CouponInfo.IssueHistoryItem> result) {
            return new IssueHistoryPageResponse(
                    result.content().stream().map(IssueHistoryResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }

    // ---------- 대고객 응답 ----------

    public record IssuedResponse(
            Long id,
            Long templateId,
            String couponName,
            DiscountType discountType,
            long discountValue,
            long minOrderAmount,
            CouponStatus status,
            LocalDateTime expiresAt
    ) {

        public static IssuedResponse from(CouponInfo.Issued info) {
            return new IssuedResponse(
                    info.id(),
                    info.templateId(),
                    info.couponName(),
                    info.discountType(),
                    info.discountValue(),
                    info.minOrderAmount(),
                    info.status(),
                    info.expiresAt()
            );
        }
    }

    public record MyCouponResponse(
            Long id,
            String couponName,
            DiscountType discountType,
            long discountValue,
            long minOrderAmount,
            CouponStatus status,
            LocalDateTime expiresAt
    ) {

        public static MyCouponResponse from(CouponInfo.MyCoupon info) {
            return new MyCouponResponse(
                    info.id(),
                    info.couponName(),
                    info.discountType(),
                    info.discountValue(),
                    info.minOrderAmount(),
                    info.status(),
                    info.expiresAt()
            );
        }
    }

    public record MyCouponPageResponse(
            List<MyCouponResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static MyCouponPageResponse from(PageResult<CouponInfo.MyCoupon> result) {
            return new MyCouponPageResponse(
                    result.content().stream().map(MyCouponResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }
}
