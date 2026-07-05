package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * streamer 가 바라보는 coupon_policy 의 "발급 가능 수량(quota)" 투영.
 * 할인 규칙(type/value/min/expiry)은 ECST 로 발급요청 이벤트가 실어 오므로 streamer 는 알 필요가 없고,
 * 선착순 소진 판정에 필요한 한도(max_issue_count)와 누적 발급수(issued_count)만 매핑한다.
 */
@Entity
@Table(name = "coupon_policy")
public class CouponQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_issue_count")
    private Long maxIssueCount;

    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    protected CouponQuota() {}

    public CouponQuota(Long maxIssueCount) {
        this.maxIssueCount = maxIssueCount;
        this.issuedCount = 0L;
    }

    public Long getId() {
        return id;
    }

    public Long getMaxIssueCount() {
        return maxIssueCount;
    }

    public long getIssuedCount() {
        return issuedCount;
    }
}
