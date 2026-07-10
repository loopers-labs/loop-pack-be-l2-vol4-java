package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "coupon_issue_request",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"request_id"}),
                @UniqueConstraint(columnNames = {"coupon_template_id", "user_id"})
        }
)
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueRequestStatus status;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Column(name = "failure_reason")
    private String failureReason;

    protected CouponIssueRequestModel() {
    }

    public CouponIssueRequestModel(String requestId, Long couponTemplateId, Long userId) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "requestId는 필수입니다.");
        }
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponTemplateId는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        this.requestId = requestId;
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public void validateRequester(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "해당 발급 요청에 접근할 수 없습니다.");
        }
    }

    public void complete(Long issuedCouponId) {
        if (this.status != CouponIssueRequestStatus.PENDING) {
            return;
        }
        this.status = CouponIssueRequestStatus.ISSUED;
        this.issuedCouponId = issuedCouponId;
    }

    public void fail(String reason) {
        if (this.status != CouponIssueRequestStatus.PENDING) {
            return;
        }
        this.status = CouponIssueRequestStatus.FAILED;
        this.failureReason = reason;
    }
}
