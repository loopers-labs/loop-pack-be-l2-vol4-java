package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선착순 쿠폰 발급 요청 — 발급 "시도" 한 건의 생애주기를 소유하는 애그리거트. api 가 PENDING 으로 접수(INSERT)하고,
 * 발급 소비자(같은 api 호스팅, 파티션 직렬화)가 결과로 전이시킨다. 클라이언트는 {@code requestId} 로 상태를 폴링한다.
 *
 * <p><b>상태기계</b>: {@code PENDING → SUCCESS / SOLD_OUT / ALREADY_ISSUED / FAILED}. 전이는 PENDING 에서만 허용되며
 * ({@link #assertPending()}) 터미널은 되돌릴 수 없다 — 이 불변식이 "이미 처리된 요청의 재처리(이중발급)"를 막는다.
 * 이 덕분에 {@code requestId} 는 소비자 <b>멱등 키</b>를 겸한다(재전달 메시지는 이미 터미널인 요청을 만나 skip).</p>
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupon_issue_requests")
public class CouponIssueRequest extends BaseEntity {

    /** 발급 요청의 전역 유일 키 — 클라이언트 폴링 핸들 · 소비자 멱등 기준. 순차 Long PK 를 노출하지 않도록 UUID 를 쓴다. */
    @Column(name = "request_id", nullable = false, unique = true, updatable = false, length = 36)
    private String requestId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueStatus status;

    private CouponIssueRequest(String requestId, Long userId, Long templateId) {
        validateRequestId(requestId);
        validateUserId(userId);
        validateTemplateId(templateId);
        this.requestId = requestId;
        this.userId = userId;
        this.templateId = templateId;
        this.status = CouponIssueStatus.PENDING;
    }

    /** 접수 시점에 PENDING 상태로 새 발급 요청을 만든다. */
    public static CouponIssueRequest pending(String requestId, Long userId, Long templateId) {
        return new CouponIssueRequest(requestId, userId, templateId);
    }

    /** 소비자 멱등 판정 — 아직 처리되지 않은(전이 가능한) 요청인지. */
    public boolean isPending() {
        return status == CouponIssueStatus.PENDING;
    }

    /** 발급 성공 — SUCCESS 로 전이(발급된 쿠폰은 user_coupons 에서 조회). */
    public void markSuccess() {
        assertPending();
        this.status = CouponIssueStatus.SUCCESS;
    }

    /** 한도 소진 — SOLD_OUT 으로 전이. */
    public void markSoldOut() {
        assertPending();
        this.status = CouponIssueStatus.SOLD_OUT;
    }

    /** 이미 발급받은 유저의 재요청 — ALREADY_ISSUED 로 전이(슬롯 미소모). */
    public void markAlreadyIssued() {
        assertPending();
        this.status = CouponIssueStatus.ALREADY_ISSUED;
    }

    /** 결정적 실패(재시도 무의미) — FAILED 로 전이. */
    public void markFailed() {
        assertPending();
        this.status = CouponIssueStatus.FAILED;
    }

    /**
     * 터미널 불변식 — PENDING 이 아니면 어떤 전이도 거부한다.
     * 재전달된 메시지가 이미 처리된 요청을 다시 전이(이중발급)시키는 것을 막는 핵심 가드다.
     */
    private void assertPending() {
        if (status != CouponIssueStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 처리된 발급 요청입니다. (status: " + status + ")");
        }
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 식별자는 비어있을 수 없습니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 사용자는 비어있을 수 없습니다.");
        }
    }

    private void validateTemplateId(Long templateId) {
        if (templateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 템플릿은 비어있을 수 없습니다.");
        }
    }
}
