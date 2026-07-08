package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponIssueRequestRecord;
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

    private CouponIssueRequestJpaEntity(String requestId, Long couponId, String userLoginId, ZonedDateTime requestedAt) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userLoginId = userLoginId;
        this.status = IssueRequestStatus.REQUESTED;
        this.requestedAt = requestedAt;
    }

    public static CouponIssueRequestJpaEntity request(String requestId, Long couponId, String userLoginId, ZonedDateTime requestedAt) {
        return new CouponIssueRequestJpaEntity(requestId, couponId, userLoginId, requestedAt);
    }

    public CouponIssueRequestRecord toRecord() {
        return CouponIssueRequestRecord.restore(
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

    public void update(CouponIssueRequestRecord record) {
        this.status = record.getStatus();
        this.rejectReason = record.getRejectReason();
        this.issuedCouponId = record.getIssuedCouponId();
        this.processedAt = record.getProcessedAt();
    }
}
