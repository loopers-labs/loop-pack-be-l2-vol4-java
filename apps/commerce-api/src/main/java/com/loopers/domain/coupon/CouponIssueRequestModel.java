package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선착순 쿠폰 발급 요청. api 가 PENDING 으로 접수해 발행하고, streamer 가 발급 처리 후 상태를 갱신한다(SQL).
 * requestId(UUID)는 사용자가 결과를 polling 하는 안정 키다.
 */
@Entity
@Table(
        name = "coupon_issue_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon_issue_request_id", columnNames = "request_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueStatus status;

    @Column(name = "reason")
    private String reason;

    private CouponIssueRequestModel(String requestId, Long couponId, Long userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatus.PENDING;
    }

    public static CouponIssueRequestModel of(String requestId, Long couponId, Long userId) {
        return new CouponIssueRequestModel(requestId, couponId, userId);
    }
}