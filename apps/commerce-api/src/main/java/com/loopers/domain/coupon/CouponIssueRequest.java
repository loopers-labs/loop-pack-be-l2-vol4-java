package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 선착순 쿠폰 발급 요청 — 비동기 발급의 접수증이자 결과 조회(polling) 대상.
 *
 * <p>API 는 이 요청을 PENDING 으로 저장(+outbox 발행)하고 즉시 202 로 응답한다.
 * 실제 발급은 Kafka 컨슈머가 수행하며, 결과(ISSUED/FAILED)를 이 행에 기록한다.
 * 유저는 {@code requestId} 로 처리 결과를 폴링한다.
 *
 * <p><strong>멱등 장부 겸용</strong>: 요청 1건 = Kafka 메시지 1건이므로, 이 행의 상태 전이
 * (PENDING → terminal)가 컨슈머의 멱등 가드를 겸한다 — 재전달된 메시지는 이미 terminal 이면 건너뛴다.
 * (streamer 의 event_handled 와 같은 역할을 요청 행 자체가 수행)
 */
@Entity
@Table(name = "coupon_issue_requests", indexes = {
    @Index(name = "idx_cir_request_id", columnList = "request_id", unique = true)
})
public class CouponIssueRequest extends BaseEntity {

    public enum Status { PENDING, ISSUED, FAILED }

    /** 외부 노출용 접수 식별자(UUID) — 폴링 키이자 Kafka 메시지의 eventId. */
    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    /** 발급 성공 시 생성된 UserCoupon id. */
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    protected CouponIssueRequest() {}

    private CouponIssueRequest(String requestId, Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponId는 필수입니다.");
        }
        this.requestId = requestId;
        this.userId = userId;
        this.couponId = couponId;
        this.status = Status.PENDING;
    }

    public static CouponIssueRequest accept(Long userId, Long couponId) {
        return new CouponIssueRequest(UUID.randomUUID().toString(), userId, couponId);
    }

    public static CouponIssueRequest accept(String requestId, Long userId, Long couponId) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "requestId is required.");
        }
        return new CouponIssueRequest(requestId, userId, couponId);
    }

    /** 발급 성공 — PENDING 에서만 전이 가능(멱등 가드는 호출부 isTerminal 체크가 담당). */
    public void markIssued(Long userCouponId) {
        requirePending();
        this.status = Status.ISSUED;
        this.userCouponId = userCouponId;
        this.processedAt = ZonedDateTime.now();
    }

    /** 발급 실패(수량 소진/중복/만료 등 확정 실패) — 사유를 남겨 폴링 응답에 노출한다. */
    public void markFailed(String reason) {
        requirePending();
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.processedAt = ZonedDateTime.now();
    }

    private void requirePending() {
        if (this.status != Status.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태의 요청만 처리할 수 있습니다.");
        }
    }

    public boolean isTerminal() {
        return this.status != Status.PENDING;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
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

    public Status getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public ZonedDateTime getProcessedAt() {
        return processedAt;
    }
}
