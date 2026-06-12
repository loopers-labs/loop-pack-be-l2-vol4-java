package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class CouponDto {

    public record IssueResponse(Long userCouponId) {
        public static IssueResponse from(Long id) {
            return new IssueResponse(id);
        }
    }

    public record MyCouponResponse(Long id, String templateName, LocalDateTime expiredAt, CouponStatus status) {
        public static MyCouponResponse from(UserCouponInfo info) {
            return new MyCouponResponse(info.id(), info.templateName(), info.expiredAt(), info.status());
        }
    }

    public record TemplateCreateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.") String name,
        @NotNull CouponType type,
        @NotNull Long value,
        Long minOrderAmount,
        @NotNull LocalDateTime expiredAt
    ) {}

    public record TemplateUpdateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.") String name,
        boolean isActive
    ) {}

    public record TemplateResponse(
        Long id, String name, CouponType type, Long value,
        Long minOrderAmount, LocalDateTime expiredAt, boolean isActive
    ) {
        public static TemplateResponse from(CouponTemplateModel template) {
            return new TemplateResponse(
                template.getId(), template.getName(), template.getType(),
                template.getValue(), template.getMinOrderAmount(), template.getExpiredAt(), template.isActive()
            );
        }
    }

    public record TemplatePageResponse(List<TemplateResponse> templates, long totalElements, int totalPages) {}

    public record IssuanceResponse(Long id, Long memberId) {}

    public record IssuancePageResponse(List<IssuanceResponse> issuances, long totalElements, int totalPages) {}
}
