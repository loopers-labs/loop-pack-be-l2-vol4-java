package com.loopers.domain.coupon;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "outbox.relay.enabled=false")
class CouponIssueServiceIntegrationTest {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createCoupon(int quantity) {
        return couponService.createCoupon(
                "선착순 쿠폰", CouponType.FIXED, 1000L, 0L,
                LocalDateTime.of(2999, 12, 31, 23, 59, 59), quantity
        ).getId();
    }

    @DisplayName("발급 요청을 접수하면, 요청이 PENDING 으로 저장되고 coupon-issue-requests 아웃박스가 원자로 적재된다.")
    @Test
    void accepts_createsPendingRequestAndOutbox() {
        // given
        Long couponId = createCoupon(100);

        // when
        CouponIssueRequestModel request = couponIssueService.request(1L, couponId);

        // then
        assertThat(request.getStatus()).isEqualTo(CouponIssueStatus.PENDING);
        assertThat(request.getRequestId()).isNotBlank();
        assertThat(couponIssueService.getRequest(request.getRequestId()).getStatus()).isEqualTo(CouponIssueStatus.PENDING);

        List<OutboxModel> rows = outboxJpaRepository.findAll().stream()
                .filter(row -> "COUPON_ISSUE_REQUESTED".equals(row.getEventType()))
                .toList();
        assertThat(rows).hasSize(1);
        OutboxModel row = rows.getFirst();
        assertThat(row.getTopic()).isEqualTo("coupon-issue-requests");
        assertThat(row.getAggregateId()).isEqualTo(couponId);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getPayload()).contains(request.getRequestId());
    }

    @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 로 거절된다.")
    @Test
    void request_throwsNotFound_whenCouponMissing() {
        assertThatThrownBy(() -> couponIssueService.request(1L, 999999L))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }
}