package com.loopers.application.event.relay;

import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.domain.event.outbox.OutboxStatus;
import com.loopers.support.monitoring.EventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class EventRelayWorkerTest {

    @DisplayName("Pending outbox를 Kafka로 발행할 때, ")
    @Nested
    class RelayPendingEvents {

        @DisplayName("발행이 성공하면 outbox를 SENT로 변경하고 성공 지표를 증가시킨다.")
        @Test
        void marksSent_whenKafkaPublishSucceeds() {
            // arrange
            TestFixture fixture = new TestFixture();
            EventOutbox outbox = fixture.saveOrderPaidOutbox("1");

            // act
            List<EventRelayWorker.RelayResult> results = fixture.worker.relayPendingEvents();

            // assert
            assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).status()).isEqualTo(EventRelayWorker.RelayResult.Status.SENT),
                () -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT),
                () -> assertThat(fixture.publisher.published).containsExactly(outbox),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.outbox.relay.success.count",
                    "topic", EventOutbox.TOPIC_ORDER_EVENTS,
                    "eventType", EventOutbox.EVENT_ORDER_PAID,
                    "result", "success"
                ).count()).isEqualTo(1.0)
            );
        }

        @DisplayName("발행이 실패하면 retry_count를 증가시키고 재시도 대상으로 남긴다.")
        @Test
        void increasesRetryCountAndKeepsPending_whenFailureIsRetryable() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.publisher.result = EventPublishResult.failed("전송 실패");
            EventOutbox outbox = fixture.saveOrderPaidOutbox("1");

            // act
            List<EventRelayWorker.RelayResult> results = fixture.worker.relayPendingEvents();

            // assert
            assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).status()).isEqualTo(EventRelayWorker.RelayResult.Status.RETRY),
                () -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING),
                () -> assertThat(outbox.getRetryCount()).isEqualTo(1),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.outbox.relay.failure.count",
                    "topic", EventOutbox.TOPIC_ORDER_EVENTS,
                    "eventType", EventOutbox.EVENT_ORDER_PAID,
                    "result", "failure"
                ).count()).isEqualTo(1.0)
            );
        }

        @DisplayName("최대 재시도 횟수에 도달하면 outbox를 FAILED로 변경한다.")
        @Test
        void marksFailed_whenRetryLimitIsReached() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.publisher.result = EventPublishResult.failed("전송 실패");
            EventOutbox outbox = fixture.saveOrderPaidOutbox("1");

            // act
            fixture.worker.relayPendingEvents();
            fixture.worker.relayPendingEvents();
            List<EventRelayWorker.RelayResult> results = fixture.worker.relayPendingEvents();

            // assert
            assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).status()).isEqualTo(EventRelayWorker.RelayResult.Status.FAILED),
                () -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED),
                () -> assertThat(outbox.getRetryCount()).isEqualTo(3)
            );
        }
    }

    private static class TestFixture {
        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final FakeEventOutboxRepository outboxRepository = new FakeEventOutboxRepository();
        private final FakeEventMessagePublisher publisher = new FakeEventMessagePublisher();
        private final EventRelayResultService resultService = new EventRelayResultService(outboxRepository);
        private final EventRelayWorker worker = new EventRelayWorker(
            outboxRepository,
            publisher,
            resultService,
            new EventMetrics(meterRegistry)
        );

        private EventOutbox saveOrderPaidOutbox(String orderId) {
            EventOutbox outbox = new EventOutbox(
                EventOutbox.TOPIC_ORDER_EVENTS,
                orderId,
                EventOutbox.EVENT_ORDER_PAID,
                EventOutbox.AGGREGATE_ORDER,
                orderId,
                "{\"orderId\":" + orderId + "}"
            );
            outboxRepository.save(outbox);
            return outbox;
        }
    }

    private static class FakeEventOutboxRepository implements EventOutboxRepository {
        private final List<EventOutbox> outboxes = new ArrayList<>();

        @Override
        public EventOutbox save(EventOutbox outbox) {
            if (!outboxes.contains(outbox)) {
                outboxes.add(outbox);
            }
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

    private static class FakeEventMessagePublisher implements EventMessagePublisher {
        private EventPublishResult result = EventPublishResult.success();
        private final List<EventOutbox> published = new ArrayList<>();

        @Override
        public EventPublishResult publish(EventOutbox outbox) {
            published.add(outbox);
            return result;
        }
    }
}
