package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record CreateTemplateRequest(
            @NotBlank String name,
            @NotNull CouponType type,
            @NotNull Long value,
            Long minOrderAmount,
            @NotNull ZonedDateTime expiredAt
    ) {}

    public record UpdateTemplateRequest(
            @NotBlank String name,
            Long minOrderAmount,
            @NotNull ZonedDateTime expiredAt
    ) {}

    public record TemplateListResponse(
            Long couponTemplateId,
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            ZonedDateTime createdAt
    ) {
        public static TemplateListResponse from(CouponTemplateInfo info) {
            return new TemplateListResponse(
                    info.templateId(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.createdAt()
            );
        }
    }

    public record TemplateDetailResponse(
            Long couponTemplateId,
            String name,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static TemplateDetailResponse from(CouponTemplateInfo info) {
            return new TemplateDetailResponse(
                    info.templateId(),
                    info.name(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record IssueHistoryResponse(
            Long couponId,
            String templateName,
            CouponType type,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            CouponStatus status
    ) {
        public static IssueHistoryResponse from(CouponInfo info) {
            return new IssueHistoryResponse(
                    info.couponId(),
                    info.templateName(),
                    info.type(),
                    info.value(),
                    info.minOrderAmount(),
                    info.expiredAt(),
                    info.status()
            );
        }
    }
}
