package com.loopers.interfaces.api.coupon;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.coupon.CouponAdminInfo;
import com.loopers.application.coupon.CouponCreateInfo;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.application.coupon.CouponUpdateInfo;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CouponAdminV1Dto {

    public record CreateRequest(
        @NotBlank(message = "쿠폰 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        @NotNull(message = "할인 타입은 null일 수 없습니다.")
        DiscountType discountType,

        @NotNull(message = "할인 값은 null일 수 없습니다.")
        Integer discountValue,

        Integer minOrderAmount,

        @NotNull(message = "만료 시각은 null일 수 없습니다.")
        ZonedDateTime expiredAt
    ) {
    }

    public record CreateResponse(Long couponId) {

        public static CreateResponse from(CouponCreateInfo couponCreateInfo) {
            return new CreateResponse(couponCreateInfo.couponId());
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "쿠폰 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        @NotNull(message = "할인 타입은 null일 수 없습니다.")
        DiscountType discountType,

        @NotNull(message = "할인 값은 null일 수 없습니다.")
        Integer discountValue,

        Integer minOrderAmount,

        @NotNull(message = "만료 시각은 null일 수 없습니다.")
        ZonedDateTime expiredAt
    ) {
    }

    public record UpdateResponse(Long couponId) {

        public static UpdateResponse from(CouponUpdateInfo couponUpdateInfo) {
            return new UpdateResponse(couponUpdateInfo.couponId());
        }
    }

    public record DetailResponse(
        Long couponId,
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {

        public static DetailResponse from(CouponAdminInfo couponAdminInfo) {
            return new DetailResponse(
                couponAdminInfo.couponId(),
                couponAdminInfo.name(),
                couponAdminInfo.discountType(),
                couponAdminInfo.discountValue(),
                couponAdminInfo.minOrderAmount(),
                couponAdminInfo.expiredAt(),
                couponAdminInfo.createdAt(),
                couponAdminInfo.updatedAt()
            );
        }
    }

    public record PageResponse(
        List<DetailResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(Page<CouponAdminInfo> couponsInfo) {
            List<DetailResponse> content = couponsInfo.getContent()
                .stream()
                .map(DetailResponse::from)
                .toList();

            return new PageResponse(
                content,
                couponsInfo.getNumber(),
                couponsInfo.getSize(),
                couponsInfo.getTotalElements(),
                couponsInfo.getTotalPages()
            );
        }
    }

    public record IssueResponse(
        Long userCouponId,
        Long userId,
        UserCouponStatus status,
        ZonedDateTime issuedAt
    ) {

        public static IssueResponse from(CouponIssueInfo couponIssueInfo) {
            return new IssueResponse(
                couponIssueInfo.userCouponId(),
                couponIssueInfo.userId(),
                couponIssueInfo.status(),
                couponIssueInfo.issuedAt()
            );
        }
    }

    public record IssuePageResponse(
        List<IssueResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static IssuePageResponse from(Page<CouponIssueInfo> issuesInfo) {
            List<IssueResponse> content = issuesInfo.getContent()
                .stream()
                .map(IssueResponse::from)
                .toList();

            return new IssuePageResponse(
                content,
                issuesInfo.getNumber(),
                issuesInfo.getSize(),
                issuesInfo.getTotalElements(),
                issuesInfo.getTotalPages()
            );
        }
    }
}
