package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponQuota;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponQuotaJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponIssueRequestsConsumerIntegrationTest {

    private static final Acknowledgment NO_OP_ACK = () -> { };

    private final CouponIssueRequestsConsumer consumer;
    private final CouponQuotaJpaRepository couponQuotaJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponIssueRequestsConsumerIntegrationTest(
        CouponIssueRequestsConsumer consumer,
        CouponQuotaJpaRepository couponQuotaJpaRepository,
        CouponIssueRequestJpaRepository couponIssueRequestJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        EventHandledJpaRepository eventHandledJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.consumer = consumer;
        this.couponQuotaJpaRepository = couponQuotaJpaRepository;
        this.couponIssueRequestJpaRepository = couponIssueRequestJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("발급 요청 이벤트를 소비할 때, ")
    @Nested
    class Consume {

        @DisplayName("한도가 남아 있으면 UserCoupon 을 발급하고 요청을 ISSUED 로 확정하며 발급수가 1 증가한다.")
        @Test
        void issuesCoupon_whenUnderLimit() {
            // given
            CouponQuota quota = couponQuotaJpaRepository.save(new CouponQuota(1L));
            CouponIssueRequest request = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));

            // when
            consumer.consume(List.of(record("evt-1", request.getId(), 1L, quota.getId())), NO_OP_ACK);

            // then
            assertAll(
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(reloadRequest(request.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(reloadQuota(quota.getId()).getIssuedCount()).isEqualTo(1L),
                () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
            );
        }

        @DisplayName("한도가 소진되면 발급하지 않고 요청을 REJECTED 로 확정한다.")
        @Test
        void rejects_whenSoldOut() {
            // given
            CouponQuota quota = couponQuotaJpaRepository.save(new CouponQuota(1L));
            CouponIssueRequest first = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));
            CouponIssueRequest second = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(2L, quota.getId()));

            // when : 한도 1 인데 두 건 소비
            consumer.consume(List.of(record("evt-1", first.getId(), 1L, quota.getId())), NO_OP_ACK);
            consumer.consume(List.of(record("evt-2", second.getId(), 2L, quota.getId())), NO_OP_ACK);

            // then
            assertAll(
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(reloadRequest(first.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(reloadRequest(second.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.REJECTED),
                () -> assertThat(reloadQuota(quota.getId()).getIssuedCount()).isEqualTo(1L)
            );
        }

        @DisplayName("같은 event_id 를 두 번 소비해도 발급은 한 번만 일어난다 — 멱등.")
        @Test
        void issuesOnce_whenSameEventConsumedTwice() {
            // given
            CouponQuota quota = couponQuotaJpaRepository.save(new CouponQuota(1L));
            CouponIssueRequest request = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));

            // when : 같은 이벤트 재전송
            consumer.consume(List.of(record("evt-1", request.getId(), 1L, quota.getId())), NO_OP_ACK);
            consumer.consume(List.of(record("evt-1", request.getId(), 1L, quota.getId())), NO_OP_ACK);

            // then
            assertAll(
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(reloadQuota(quota.getId()).getIssuedCount()).isEqualTo(1L),
                () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
            );
        }

        @DisplayName("같은 유저가 같은 정책에 서로 다른 요청 2건을 넣어도, 쿠폰은 한 번만 발급되고 둘째 요청은 REJECTED 된다.")
        @Test
        void issuesOnce_whenSameUserRequestsTwice() {
            // given
            CouponQuota quota = couponQuotaJpaRepository.save(new CouponQuota(100L));
            CouponIssueRequest first = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));
            CouponIssueRequest second = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));

            // when : 같은 유저(1L)의 서로 다른 요청(다른 requestId·eventId) 순차 소비
            consumer.consume(List.of(record("evt-1", first.getId(), 1L, quota.getId())), NO_OP_ACK);
            consumer.consume(List.of(record("evt-2", second.getId(), 1L, quota.getId())), NO_OP_ACK);

            // then
            assertAll(
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(reloadRequest(first.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(reloadRequest(second.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.REJECTED),
                () -> assertThat(reloadQuota(quota.getId()).getIssuedCount()).isEqualTo(1L)
            );
        }

        @DisplayName("중복 요청은 한도를 소비하지 않는다 — 한도 2, 유저 A 2회·유저 B 1회면 A·B 각 1건씩만 발급되고 발급수는 2 다.")
        @Test
        void duplicateDoesNotConsumeQuota() {
            // given
            CouponQuota quota = couponQuotaJpaRepository.save(new CouponQuota(2L));
            CouponIssueRequest aFirst = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));
            CouponIssueRequest aSecond = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(1L, quota.getId()));
            CouponIssueRequest b = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(2L, quota.getId()));

            // when : A 가 2번, B 가 1번 요청
            consumer.consume(List.of(record("evt-a1", aFirst.getId(), 1L, quota.getId())), NO_OP_ACK);
            consumer.consume(List.of(record("evt-a2", aSecond.getId(), 1L, quota.getId())), NO_OP_ACK);
            consumer.consume(List.of(record("evt-b", b.getId(), 2L, quota.getId())), NO_OP_ACK);

            // then : A 의 중복 요청이 한도를 잡아먹지 않아 B 도 정상 발급된다
            assertAll(
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(2L),
                () -> assertThat(reloadRequest(aFirst.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(reloadRequest(aSecond.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.REJECTED),
                () -> assertThat(reloadRequest(b.getId()).getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(reloadQuota(quota.getId()).getIssuedCount()).isEqualTo(2L)
            );
        }
    }

    private CouponIssueRequest reloadRequest(Long id) {
        return couponIssueRequestJpaRepository.findById(id).orElseThrow();
    }

    private CouponQuota reloadQuota(Long id) {
        return couponQuotaJpaRepository.findById(id).orElseThrow();
    }

    private ConsumerRecord<String, byte[]> record(String eventId, Long requestId, long userId, Long couponPolicyId) {
        String json = """
            {"eventId":"%s","eventType":"COUPON_ISSUE_REQUESTED","aggregateId":%d,
             "data":{"requestId":%d,"userId":%d,"couponPolicyId":%d,"type":"FIXED",
                     "discountValue":3000,"minOrderAmount":10000,"expiredAt":"2099-12-31T23:59:59+09:00"}}
            """.formatted(eventId, couponPolicyId, requestId, userId, couponPolicyId);
        return new ConsumerRecord<>("coupon-issue-requests", 0, 0L, String.valueOf(couponPolicyId),
            json.getBytes(StandardCharsets.UTF_8));
    }
}
