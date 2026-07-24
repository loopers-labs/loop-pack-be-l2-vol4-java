package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 스트리머 측 coupon_issue_request 매핑. 상태 갱신은 네이티브 UPDATE(request_id 기준)로 수행하므로
 * 이 엔티티는 JpaRepository 타입 지정용이다. 테이블 소유·생성은 commerce-api와 공유한다.
 */
@Entity
@Table(
    name = "coupon_issue_request",
    uniqueConstraints = @UniqueConstraint(name = "uk_coupon_issue_request", columnNames = "request_id")
)
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(nullable = false, length = 20)
    private String status;

    protected CouponIssueRequestModel() {}
}
