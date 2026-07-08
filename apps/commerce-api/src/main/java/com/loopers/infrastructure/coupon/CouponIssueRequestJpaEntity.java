package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.IssueRejectReason;
import com.loopers.domain.coupon.IssueRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_issue_request")
public class CouponIssueRequestJpaEntity extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String requestId;

    @Column(nullable = false, updatable = false)
    private Long couponId;

    @Column(nullable = false, updatable = false)
    private String userLoginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private IssueRejectReason rejectReason;

    @Column
    private Long issuedCouponId;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime requestedAt;

    @Column
    private ZonedDateTime processedAt;

    protected CouponIssueRequestJpaEntity() {
    }

    private CouponIssueRequestJpaEntity(CouponIssueRequest request) {
        this.requestId = request.getRequestId();
        this.couponId = request.getCouponId();
        this.userLoginId = request.getUserLoginId();
        this.status = request.getStatus();
        this.rejectReason = request.getRejectReason();
        this.issuedCouponId = request.getIssuedCouponId();
        this.requestedAt = request.getRequestedAt();
        this.processedAt = request.getProcessedAt();
    }

    public static CouponIssueRequestJpaEntity from(CouponIssueRequest request) {
        return new CouponIssueRequestJpaEntity(request);
    }

    public void update(CouponIssueRequest request) {
        this.status = request.getStatus();
        this.rejectReason = request.getRejectReason();
        this.issuedCouponId = request.getIssuedCouponId();
        this.processedAt = request.getProcessedAt();
    }

    public CouponIssueRequest toDomain() {
        return CouponIssueRequest.reconstruct(
            requestId,
            couponId,
            userLoginId,
            status,
            rejectReason,
            issuedCouponId,
            requestedAt,
            processedAt
        );
    }
}
