package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssueRejectReason;
import com.loopers.domain.coupon.IssueRequestStatus;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.admin.auto-create=false"
})
class FirstComeCouponIssueProcessorIntegrationTest {

    private final FirstComeCouponIssueProcessor processor;
    private final CouponJpaRepository couponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    FirstComeCouponIssueProcessorIntegrationTest(
        FirstComeCouponIssueProcessor processor,
        CouponJpaRepository couponJpaRepository,
        CouponIssueRequestJpaRepository couponIssueRequestJpaRepository,
        IssuedCouponJpaRepository issuedCouponJpaRepository,
        EventHandledJpaRepository eventHandledJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.processor = processor;
        this.couponJpaRepository = couponJpaRepository;
        this.couponIssueRequestJpaRepository = couponIssueRequestJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("선착순 쿠폰 요청을 처리하면 발급 쿠폰을 만들고 요청 상태를 ISSUED로 확정한다.")
    @Test
    void issuesCouponAndMarksRequestIssued_whenStockRemains() {
        // arrange
        CouponJpaEntity coupon = couponJpaRepository.save(CouponJpaEntity.firstCome("선착순", ZonedDateTime.now().plusDays(7), 1L));
        couponIssueRequestJpaRepository.save(CouponIssueRequestJpaEntity.request("request-1", coupon.getId(), "user1234", ZonedDateTime.now()));

        // act
        boolean processed = processor.process(issueRequested("event-1", "request-1", coupon.getId(), "user1234"));

        // assert
        var request = couponIssueRequestJpaRepository.findByRequestIdAndDeletedAtIsNull("request-1").orElseThrow().toRecord();
        assertAll(
            () -> assertThat(processed).isTrue(),
            () -> assertThat(request.getStatus()).isEqualTo(IssueRequestStatus.ISSUED),
            () -> assertThat(request.getIssuedCouponId()).isNotNull(),
            () -> assertThat(issuedCouponJpaRepository.findByCouponIdAndUserLoginIdAndDeletedAtIsNull(coupon.getId(), "user1234")).isPresent(),
            () -> assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue()
        );
    }

    @DisplayName("발급 한도가 소진되면 요청 상태를 REJECTED/SOLD_OUT으로 확정한다.")
    @Test
    void rejectsRequest_whenCouponIsSoldOut() {
        // arrange
        CouponJpaEntity coupon = couponJpaRepository.save(CouponJpaEntity.firstCome("선착순", ZonedDateTime.now().plusDays(7), 1L));
        couponIssueRequestJpaRepository.save(CouponIssueRequestJpaEntity.request("request-1", coupon.getId(), "user1234", ZonedDateTime.now()));
        couponIssueRequestJpaRepository.save(CouponIssueRequestJpaEntity.request("request-2", coupon.getId(), "user5678", ZonedDateTime.now()));
        processor.process(issueRequested("event-1", "request-1", coupon.getId(), "user1234"));

        // act
        boolean processed = processor.process(issueRequested("event-2", "request-2", coupon.getId(), "user5678"));

        // assert
        var request = couponIssueRequestJpaRepository.findByRequestIdAndDeletedAtIsNull("request-2").orElseThrow().toRecord();
        assertAll(
            () -> assertThat(processed).isTrue(),
            () -> assertThat(request.getStatus()).isEqualTo(IssueRequestStatus.REJECTED),
            () -> assertThat(request.getRejectReason()).isEqualTo(IssueRejectReason.SOLD_OUT),
            () -> assertThat(issuedCouponJpaRepository.findByCouponIdAndUserLoginIdAndDeletedAtIsNull(coupon.getId(), "user5678")).isEmpty()
        );
    }

    private CouponIssueRequestMessage issueRequested(String eventId, String requestId, Long couponId, String userLoginId) {
        return new CouponIssueRequestMessage(
            eventId,
            "COUPON_ISSUE_REQUESTED",
            "COUPON",
            couponId,
            ZonedDateTime.now(),
            Map.of(
                "couponId", couponId,
                "userLoginId", userLoginId,
                "requestId", requestId
            )
        );
    }
}
