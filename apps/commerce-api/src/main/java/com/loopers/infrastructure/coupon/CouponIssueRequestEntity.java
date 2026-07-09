package com.loopers.infrastructure.coupon;

import com.loopers.domain.AuditEntity;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.domain.Persistable;

import java.time.ZonedDateTime;

/**
 * coupon_issue_request 테이블 JPA 매핑 전용 엔티티 (Slice 4, Step3). 순수 도메인(CouponIssueRequestModel)과 분리.
 *
 * <p>PK는 앱 생성 TSID라 신규에도 id가 채워져 있으므로, BaseEntity(IDENTITY) 대신 id 없는 {@link AuditEntity}를
 * 상속하고 @Id를 직접 선언한다. Spring Data가 INSERT/UPDATE를 오판하지 않도록 {@link Persistable#isNew()}로
 * "아직 영속 전(createdAt == null)"임을 알린다(order와 동일 전략).
 *
 * <p>{@code UNIQUE(user_id, coupon_id)}로 1인 1매를 요청 단계에서 강제한다. status/processed_at은 발급 확정 시
 * commerce-streamer가 직접 UPDATE한다(commerce-api는 접수 후 이 행을 수정하지 않는다).
 */
@Entity
@Table(
        name = "coupon_issue_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_cir_user_coupon", columnNames = {"user_id", "coupon_id"})
)
public class CouponIssueRequestEntity extends AuditEntity implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    protected CouponIssueRequestEntity() {}

    public CouponIssueRequestEntity(Long id, Long userId, Long couponId, CouponIssueRequestStatus status,
                                    ZonedDateTime requestedAt, ZonedDateTime processedAt) {
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    @Override
    public Long getId() {
        return id;
    }

    /** 아직 영속 전이면 createdAt이 비어 있다(@PrePersist에서 채워짐) → INSERT(persist)로 처리하게 한다. */
    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public CouponIssueRequestStatus getStatus() {
        return status;
    }

    public ZonedDateTime getRequestedAt() {
        return requestedAt;
    }

    public ZonedDateTime getProcessedAt() {
        return processedAt;
    }
}
