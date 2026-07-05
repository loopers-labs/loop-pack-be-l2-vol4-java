package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.kafka.event.CouponIssueRequestEventPayload;
import com.loopers.kafka.event.EventMessage;
import com.loopers.support.monitoring.EventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class CouponIssueRequestEventServiceTest {

    @DisplayName("쿠폰 발급 요청을 접수할 때, ")
    @Nested
    class RequestIssue {

        @DisplayName("발급 요청과 coupon-issue-requests outbox를 함께 저장한다.")
        @Test
        void savesIssueRequestAndOutbox() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.couponTemplateRepository.saveTemplate(template(1L, null, 1));

            // act
            CouponResult.IssueRequest result = fixture.commandService.requestIssue(1L, "user1");

            // assert
            EventOutbox outbox = fixture.eventOutboxRepository.outboxes.get(0);
            CouponIssueRequestEventPayload payload = fixture.objectMapper.readValue(
                outbox.getPayload(),
                CouponIssueRequestEventPayload.class
            );
            assertAll(
                () -> assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING),
                () -> assertThat(result.requestId()).isPositive(),
                () -> assertThat(outbox.getTopic()).isEqualTo(EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS),
                () -> assertThat(outbox.getEventType()).isEqualTo(EventOutbox.EVENT_COUPON_ISSUE_REQUESTED),
                () -> assertThat(outbox.getPartitionKey()).isEqualTo("1"),
                () -> assertThat(payload.requestId()).isEqualTo(result.requestId()),
                () -> assertThat(payload.couponTemplateId()).isEqualTo(1L),
                () -> assertThat(payload.userId()).isEqualTo("user1")
            );
        }
    }

    @DisplayName("쿠폰 발급 요청 이벤트를 처리할 때, ")
    @Nested
    class Process {

        @DisplayName("새 이벤트이면 처리 이력을 남기고 실제 쿠폰을 발급한다.")
        @Test
        void issuesCoupon_whenEventIsNew() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.couponTemplateRepository.saveTemplate(template(1L, 1L, 1));
            CouponResult.IssueRequest request = fixture.commandService.requestIssue(1L, "user1");
            EventMessage message = fixture.issueRequestMessage("event-1", request.requestId(), 1L, "user1");

            // act
            CouponIssueRequestEventService.ProcessResult result = fixture.eventService.process(message);

            // assert
            CouponIssueRequest changedRequest = fixture.couponIssueRequestRepository.requests.get(request.requestId());
            assertAll(
                () -> assertThat(result).isEqualTo(CouponIssueRequestEventService.ProcessResult.ISSUED),
                () -> assertThat(fixture.eventHandledRepository.exists("event-1")).isTrue(),
                () -> assertThat(changedRequest.getStatus()).isEqualTo(CouponIssueRequestStatus.SUCCEEDED),
                () -> assertThat(changedRequest.getIssuedCouponId()).isPositive(),
                () -> assertThat(fixture.issuedCouponRepository.issuedCoupons).hasSize(1),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.kafka.consumer.success.count",
                    "topic", EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS,
                    "eventType", EventOutbox.EVENT_COUPON_ISSUE_REQUESTED,
                    "result", "success"
                ).count()).isEqualTo(1.0)
            );
        }

        @DisplayName("이미 처리한 eventId이면 실제 발급을 다시 수행하지 않는다.")
        @Test
        void skipsIssue_whenEventIsDuplicate() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.couponTemplateRepository.saveTemplate(template(1L, 1L, 1));
            CouponResult.IssueRequest request = fixture.commandService.requestIssue(1L, "user1");
            EventMessage message = fixture.issueRequestMessage("event-1", request.requestId(), 1L, "user1");
            fixture.eventService.process(message);

            // act
            CouponIssueRequestEventService.ProcessResult result = fixture.eventService.process(message);

            // assert
            assertAll(
                () -> assertThat(result).isEqualTo(CouponIssueRequestEventService.ProcessResult.DUPLICATE),
                () -> assertThat(fixture.issuedCouponRepository.issuedCoupons).hasSize(1),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.kafka.consumer.duplicate.count",
                    "topic", EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS,
                    "eventType", EventOutbox.EVENT_COUPON_ISSUE_REQUESTED,
                    "result", "duplicate"
                ).count()).isEqualTo(1.0)
            );
        }

        @DisplayName("전체 발급 한도에 도달하면 요청을 FAILED로 확정하고 메시지는 처리 완료로 본다.")
        @Test
        void rejectsIssueRequest_whenTotalIssueLimitIsExhausted() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.couponTemplateRepository.saveTemplate(template(1L, 1L, 1));
            CouponResult.IssueRequest firstRequest = fixture.commandService.requestIssue(1L, "user1");
            CouponResult.IssueRequest secondRequest = fixture.commandService.requestIssue(1L, "user2");
            fixture.eventService.process(fixture.issueRequestMessage("event-1", firstRequest.requestId(), 1L, "user1"));

            // act
            CouponIssueRequestEventService.ProcessResult result = fixture.eventService.process(
                fixture.issueRequestMessage("event-2", secondRequest.requestId(), 1L, "user2")
            );

            // assert
            CouponIssueRequest changedRequest = fixture.couponIssueRequestRepository.requests.get(secondRequest.requestId());
            assertAll(
                () -> assertThat(result).isEqualTo(CouponIssueRequestEventService.ProcessResult.REJECTED),
                () -> assertThat(changedRequest.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
                () -> assertThat(changedRequest.getFailureReason()).contains("발급 수량"),
                () -> assertThat(fixture.issuedCouponRepository.issuedCoupons).hasSize(1)
            );
        }
    }

    private static class TestFixture {
        private final ObjectMapper objectMapper = objectMapper();
        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final FakeCouponTemplateRepository couponTemplateRepository = new FakeCouponTemplateRepository();
        private final FakeIssuedCouponRepository issuedCouponRepository = new FakeIssuedCouponRepository();
        private final FakeCouponIssueRequestRepository couponIssueRequestRepository = new FakeCouponIssueRequestRepository();
        private final FakeEventOutboxRepository eventOutboxRepository = new FakeEventOutboxRepository();
        private final FakeEventHandledRepository eventHandledRepository = new FakeEventHandledRepository();
        private final CouponCommandService commandService = new CouponCommandService(
            couponTemplateRepository,
            issuedCouponRepository,
            couponIssueRequestRepository,
            eventOutboxRepository,
            objectMapper
        );
        private final CouponIssueRequestEventService eventService = new CouponIssueRequestEventService(
            commandService,
            eventHandledRepository,
            objectMapper,
            new EventMetrics(meterRegistry)
        );

        private EventMessage issueRequestMessage(
            String eventId,
            Long requestId,
            Long couponTemplateId,
            String userId
        ) throws Exception {
            ZonedDateTime occurredAt = ZonedDateTime.now();
            CouponIssueRequestEventPayload payload = new CouponIssueRequestEventPayload(
                requestId,
                couponTemplateId,
                userId,
                occurredAt
            );
            return new EventMessage(
                eventId,
                EventOutbox.EVENT_COUPON_ISSUE_REQUESTED,
                EventOutbox.AGGREGATE_COUPON_TEMPLATE,
                String.valueOf(couponTemplateId),
                objectMapper.writeValueAsString(payload),
                occurredAt
            );
        }
    }

    private static class FakeCouponTemplateRepository implements CouponTemplateRepository {
        private final Map<Long, CouponTemplate> templates = new HashMap<>();

        private void saveTemplate(CouponTemplate couponTemplate) {
            templates.put(couponTemplate.getId(), couponTemplate);
        }

        @Override
        public CouponTemplate save(CouponTemplate couponTemplate) {
            templates.put(couponTemplate.getId(), couponTemplate);
            return couponTemplate;
        }

        @Override
        public Optional<CouponTemplate> find(Long couponTemplateId) {
            return Optional.ofNullable(templates.get(couponTemplateId));
        }

        @Override
        public Optional<CouponTemplate> findActiveForUpdate(Long couponTemplateId) {
            return Optional.ofNullable(templates.get(couponTemplateId));
        }

        @Override
        public List<CouponTemplate> findAll(int page, int size) {
            return templates.values().stream().toList();
        }

        @Override
        public long countAll() {
            return templates.size();
        }
    }

    private static class FakeIssuedCouponRepository implements IssuedCouponRepository {
        private final Map<Long, IssuedCoupon> issuedCoupons = new HashMap<>();
        private long sequence = 1L;

        @Override
        public IssuedCoupon save(IssuedCoupon issuedCoupon) {
            Long id = issuedCoupon.isNew() ? sequence++ : issuedCoupon.getId();
            IssuedCoupon persisted = IssuedCoupon.reconstruct(
                id,
                issuedCoupon.getCouponTemplateId(),
                issuedCoupon.getUserId(),
                issuedCoupon.getType(),
                issuedCoupon.getValue(),
                issuedCoupon.getMinOrderAmount(),
                issuedCoupon.getExpiredAt(),
                issuedCoupon.getStoredStatus(),
                issuedCoupon.getUsedAt(),
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                null
            );
            issuedCoupons.put(id, persisted);
            return persisted;
        }

        @Override
        public Optional<IssuedCoupon> findForUpdate(Long issuedCouponId) {
            return Optional.ofNullable(issuedCoupons.get(issuedCouponId));
        }

        @Override
        public List<IssuedCoupon> findByUserId(String userId, int page, int size) {
            return issuedCoupons.values()
                .stream()
                .filter(coupon -> coupon.getUserId().equals(userId))
                .toList();
        }

        @Override
        public long countByUserId(String userId) {
            return findByUserId(userId, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, int page, int size) {
            return issuedCoupons.values()
                .stream()
                .filter(coupon -> coupon.getCouponTemplateId().equals(couponTemplateId))
                .toList();
        }

        @Override
        public long countByCouponTemplateId(Long couponTemplateId) {
            return findByCouponTemplateId(couponTemplateId, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public long countByCouponTemplateIdAndUserId(Long couponTemplateId, String userId) {
            return issuedCoupons.values()
                .stream()
                .filter(coupon -> coupon.getCouponTemplateId().equals(couponTemplateId))
                .filter(coupon -> coupon.getUserId().equals(userId))
                .count();
        }
    }

    private static class FakeCouponIssueRequestRepository implements CouponIssueRequestRepository {
        private final Map<Long, CouponIssueRequest> requests = new HashMap<>();
        private long sequence = 1L;

        @Override
        public CouponIssueRequest save(CouponIssueRequest request) {
            Long id = request.isNew() ? sequence++ : request.getId();
            ZonedDateTime now = ZonedDateTime.now();
            CouponIssueRequest persisted = CouponIssueRequest.reconstruct(
                id,
                request.getCouponTemplateId(),
                request.getUserId(),
                request.getStatus(),
                request.getIssuedCouponId(),
                request.getFailureReason(),
                request.getCompletedAt(),
                request.getCreatedAt() == null ? now : request.getCreatedAt(),
                now,
                null
            );
            requests.put(id, persisted);
            return persisted;
        }

        @Override
        public Optional<CouponIssueRequest> find(Long requestId) {
            return Optional.ofNullable(requests.get(requestId));
        }

        @Override
        public Optional<CouponIssueRequest> findByIdAndUserId(Long requestId, String userId) {
            return Optional.ofNullable(requests.get(requestId))
                .filter(request -> request.getUserId().equals(userId));
        }

        @Override
        public Optional<CouponIssueRequest> findForUpdate(Long requestId) {
            return find(requestId);
        }
    }

    private static class FakeEventOutboxRepository implements EventOutboxRepository {
        private final List<EventOutbox> outboxes = new ArrayList<>();

        @Override
        public EventOutbox save(EventOutbox outbox) {
            outboxes.add(outbox);
            return outbox;
        }

        @Override
        public List<EventOutbox> findPendingEvents(int limit) {
            return outboxes.stream()
                .filter(EventOutbox::isPending)
                .limit(limit)
                .toList();
        }
    }

    private static class FakeEventHandledRepository implements EventHandledRepository {
        private final Map<String, EventHandled> handled = new HashMap<>();

        @Override
        public boolean exists(String eventId) {
            return handled.containsKey(eventId);
        }

        @Override
        public EventHandled save(EventHandled eventHandled) {
            handled.put(eventHandled.getEventId(), eventHandled);
            return eventHandled;
        }
    }

    private static CouponTemplate template(Long id, Long totalIssueLimit, int maxIssuesPerUser) {
        ZonedDateTime now = ZonedDateTime.now();
        return CouponTemplate.reconstruct(
            id,
            "선착순 쿠폰",
            CouponType.FIXED,
            1_000L,
            null,
            totalIssueLimit,
            maxIssuesPerUser,
            now.plusDays(1),
            now,
            now,
            null
        );
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
