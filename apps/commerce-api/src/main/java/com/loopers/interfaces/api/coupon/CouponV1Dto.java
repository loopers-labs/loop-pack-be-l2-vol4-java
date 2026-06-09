package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponIssueInfo;

public class CouponV1Dto {

    public record IssueResponse(Long userCouponId) {

        public static IssueResponse from(UserCouponIssueInfo issueInfo) {
            return new IssueResponse(issueInfo.userCouponId());
        }
    }
}
