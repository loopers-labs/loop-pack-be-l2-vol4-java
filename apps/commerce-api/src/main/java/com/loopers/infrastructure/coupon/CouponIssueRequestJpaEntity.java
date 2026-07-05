package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "coupon_issue_request",
    indexes = {
        @Index(name = "idx_coupon_issue_request_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_coupon_issue_request_template_status", columnList = "coupon_template_id, status")
    }
)
public class CouponIssueRequestJpaEntity extends BaseEntity {

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueRequestStatus status;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    protected CouponIssueRequestJpaEntity() {}

    private CouponIssueRequestJpaEntity(
        Long couponTemplateId,
        String userId,
        CouponIssueRequestStatus status,
        Long issuedCouponId,
        String failureReason,
        ZonedDateTime completedAt
    ) {
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = status;
        this.issuedCouponId = issuedCouponId;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
    }

    public static CouponIssueRequestJpaEntity from(CouponIssueRequest request) {
        return new CouponIssueRequestJpaEntity(
            request.getCouponTemplateId(),
            request.getUserId(),
            request.getStatus(),
            request.getIssuedCouponId(),
            request.getFailureReason(),
            request.getCompletedAt()
        );
    }

    public CouponIssueRequest toDomain() {
        return CouponIssueRequest.reconstruct(
            getId(),
            couponTemplateId,
            userId,
            status,
            issuedCouponId,
            failureReason,
            completedAt,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(CouponIssueRequest request) {
        this.couponTemplateId = request.getCouponTemplateId();
        this.userId = request.getUserId();
        this.status = request.getStatus();
        this.issuedCouponId = request.getIssuedCouponId();
        this.failureReason = request.getFailureReason();
        this.completedAt = request.getCompletedAt();
    }
}
