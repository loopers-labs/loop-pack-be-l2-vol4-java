package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 선착순 발급 "요청"의 상태 기록. 접수 시 PENDING으로 저장되고(발행 이벤트와 같은 트랜잭션),
 * 컨슈머가 처리 결과에 따라 ISSUED / SOLD_OUT 으로 갱신한다. requestId로 폴링 조회한다.
 */
@Entity
@Table(
    name = "coupon_issue_request",
    uniqueConstraints = @UniqueConstraint(name = "uk_coupon_issue_request", columnNames = "request_id")
)
public class CouponIssueRequestModel extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String ISSUED = "ISSUED";
    public static final String SOLD_OUT = "SOLD_OUT";

    @Column(name = "request_id", nullable = false, unique = true, updatable = false, length = 36)
    private String requestId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false, updatable = false)
    private Long couponId;

    @Column(nullable = false, length = 20)
    private String status;

    protected CouponIssueRequestModel() {}

    public CouponIssueRequestModel(String requestId, Long userId, Long couponId) {
        this.requestId = requestId;
        this.userId = userId;
        this.couponId = couponId;
        this.status = PENDING;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getStatus() {
        return status;
    }
}
