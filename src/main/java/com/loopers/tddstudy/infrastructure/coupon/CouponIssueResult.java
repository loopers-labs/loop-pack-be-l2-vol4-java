package com.loopers.tddstudy.infrastructure.coupon;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue_result")
public class CouponIssueResult {

    @Id
    private String requestId;
    private Long couponId;
    private Long userId;

    @Column(columnDefinition = "varchar(20)")
    private String status;      // PENDING / SUCCESS / FAILED
    private String reason;
    private LocalDateTime updatedAt;

    protected CouponIssueResult() {}

    public CouponIssueResult(String requestId, Long couponId, Long userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = "PENDING";
        this.updatedAt = LocalDateTime.now();
    }

    public void success() { this.status = "SUCCESS"; this.reason = null; this.updatedAt = LocalDateTime.now(); }
    public void fail(String reason) { this.status = "FAILED"; this.reason = reason; this.updatedAt = LocalDateTime.now(); }

    public String getRequestId() { return requestId; }
    public Long getCouponId() { return couponId; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
