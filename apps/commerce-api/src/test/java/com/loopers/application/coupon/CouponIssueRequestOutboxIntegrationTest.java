package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestService;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.infrastructure.outbox.OutboxEntity;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxStatus;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 선착순 발급 요청 접수가 <b>같은 트랜잭션</b>으로 outbox에 적재되는지 검증한다(Slice 4, BEFORE_COMMIT 배선).
 *
 * <p>{@code CouponIssueRequestService.request}가 발행하는 {@code CouponIssueRequestedEvent}를
 * {@link CouponIssueRequestEventOutboxListener}가 커밋 직전에 받아 outbox 행으로 남긴다. 검증 포인트:
 * (1) 신규 접수는 PENDING + coupon-issue-requests outbox 적재(계약: 토픽/키/payload), (2) 멱등 재요청은
 * 새 요청·새 이벤트를 만들지 않음, (3) 미존재 requestId 조회는 NOT_FOUND.
 * (릴레이/즉시발행은 test 프로파일에서 비활성이라 status=PENDING 그대로 남는다.)
 */
@SpringBootTest
public class CouponIssueRequestOutboxIntegrationTest {

    @Autowired CouponIssueRequestService couponIssueRequestService;
    @Autowired OutboxJpaRepository outboxJpaRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private static final Long COUPON_ID = 777L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private List<OutboxEntity> couponRequestedRows() {
        return outboxJpaRepository.findAll().stream()
                .filter(o -> "COUPON_ISSUE_REQUESTED".equals(o.getEventType()))
                .toList();
    }

    @DisplayName("발급 요청하면 PENDING으로 접수되고 COUPON_ISSUE_REQUESTED가 coupon-issue-requests outbox에 적재된다.")
    @Test
    void given_request_then_pendingAndOutboxAppended() {
        CouponIssueRequestModel request = couponIssueRequestService.request(USER_ID, COUPON_ID);

        assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
        assertThat(couponRequestedRows()).singleElement().satisfies(o -> {
            assertThat(o.getTopic()).isEqualTo("coupon-issue-requests");
            assertThat(o.getAggregateType()).isEqualTo("coupon");
            assertThat(o.getAggregateId()).isEqualTo(COUPON_ID);
            assertThat(o.getPartitionKey()).isEqualTo(String.valueOf(COUPON_ID));
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(o.getPayload())
                    .contains("\"requestId\":" + request.getId())
                    .contains("\"userId\":" + USER_ID)
                    .contains("\"couponId\":" + COUPON_ID);
        });
    }

    @DisplayName("같은 사용자·쿠폰 재요청은 멱등 — 기존 requestId를 반환하고 이벤트는 1건만 적재된다(1인 1매).")
    @Test
    void given_duplicateRequest_then_sameIdAndOnlyOneOutboxRow() {
        CouponIssueRequestModel first = couponIssueRequestService.request(USER_ID, COUPON_ID);
        CouponIssueRequestModel second = couponIssueRequestService.request(USER_ID, COUPON_ID);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(couponRequestedRows()).hasSize(1);
    }

    @DisplayName("존재하지 않는 requestId 결과 조회는 NOT_FOUND로 실패한다.")
    @Test
    void given_unknownRequestId_then_notFound() {
        assertThatThrownBy(() -> couponIssueRequestService.getResult(999_999L))
                .isInstanceOf(CoreException.class);
    }
}
