package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponCreateCommand;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.CouponUpdateCommand;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record CouponCreateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @Min(1) int value,
        @Min(0) Integer minOrderAmount,
        @NotNull ZonedDateTime expiredAt,
        @Min(1) Integer totalQuantity
    ) {
        public CouponCreateCommand toCommand() {
            return new CouponCreateCommand(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        }
    }

    public record CouponUpdateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @Min(1) int value,
        @Min(0) Integer minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {
        public CouponUpdateCommand toCommand() {
            return new CouponUpdateCommand(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        int value,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        Integer totalQuantity,
        int issuedQuantity,
        ZonedDateTime createdAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(), info.name(), info.type(), info.value(),
                info.minOrderAmount(), info.expiredAt(),
                info.totalQuantity(), info.issuedQuantity(), info.createdAt()
            );
        }
    }

    public record UserCouponResponse(
        Long id,
        Long userId,
        CouponResponse coupon,
        UserCouponStatus status,
        ZonedDateTime createdAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(), info.userId(),
                CouponResponse.from(info.coupon()),
                info.status(),
                info.createdAt()
            );
        }
    }

    public record IssueRequestResponse(
        Long id,
        Long userId,
        Long couponId,
        CouponIssueRequestStatus status,
        String failureReason,
        Long userCouponId,
        ZonedDateTime createdAt
    ) {
        public static IssueRequestResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestResponse(
                info.id(), info.userId(), info.couponId(),
                info.status(), info.failureReason(), info.userCouponId(),
                info.createdAt()
            );
        }
    }
}
