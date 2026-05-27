package com.loopers.application.event.relay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.event.dataplatform.DataPlatformClient;
import com.loopers.domain.event.dataplatform.DataPlatformResult;
import com.loopers.domain.event.order.OrderPaidEvent;
import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OrderEventOutboxRepository;
import com.loopers.domain.event.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class EventRelayWorkerTest {

    @DisplayName("Pending outbox를 전송할 때, ")
    @Nested
    class RelayPendingEvents {

        @DisplayName("데이터 플랫폼 전송이 성공하면 outbox를 SENT로 변경한다.")
        @Test
        void marksSent_whenDataPlatformSendSucceeds() {
            // arrange
            TestFixture fixture = new TestFixture();
            OrderEventOutbox outbox = fixture.saveOrderPaidOutbox(1L);

            // act
            List<EventRelayWorker.RelayResult> results = fixture.worker.relayPendingEvents();

            // assert
            assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).status()).isEqualTo(EventRelayWorker.RelayResult.Status.SENT),
                () -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT),
                () -> assertThat(fixture.dataPlatformClient.sentEvents).hasSize(1),
                () -> assertThat(fixture.dataPlatformClient.sentEvents.get(0).orderId()).isEqualTo(1L),
                () -> assertThat(fixture.outboxRepository.lockedQueryCallCount).isEqualTo(1)
            );
        }

        @DisplayName("데이터 플랫폼 전송이 실패하면 retry_count를 증가시키고 재시도 대상으로 남긴다.")
        @Test
        void increasesRetryCountAndKeepsPending_whenFailureIsRetryable() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.dataPlatformClient.result = DataPlatformResult.failed("전송 실패");
            OrderEventOutbox outbox = fixture.saveOrderPaidOutbox(1L);

            // act
            List<EventRelayWorker.RelayResult> results = fixture.worker.relayPendingEvents();

            // assert
            assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).status()).isEqualTo(EventRelayWorker.RelayResult.Status.RETRY),
                () -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING),
                () -> assertThat(outbox.getRetryCount()).isEqualTo(1)
            );
        }

        @DisplayName("최대 재시도 횟수에 도달하면 outbox를 FAILED로 변경한다.")
        @Test
        void marksFailed_whenRetryLimitIsReached() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.dataPlatformClient.result = DataPlatformResult.failed("전송 실패");
            OrderEventOutbox outbox = fixture.saveOrderPaidOutbox(1L);

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
        private final ObjectMapper objectMapper = objectMapper();
        private final FakeOrderEventOutboxRepository outboxRepository = new FakeOrderEventOutboxRepository();
        private final FakeDataPlatformClient dataPlatformClient = new FakeDataPlatformClient();
        private final EventRelayResultService resultService = new EventRelayResultService(outboxRepository);
        private final EventRelayWorker worker = new EventRelayWorker(
            outboxRepository,
            dataPlatformClient,
            objectMapper,
            resultService
        );

        private OrderEventOutbox saveOrderPaidOutbox(Long orderId) {
            OrderEventOutbox outbox = new OrderEventOutbox(
                orderId,
                OrderEventOutbox.ORDER_PAID,
                serialize(new OrderPaidEvent(
                    orderId,
                    "user1",
                    2_000L,
                    ZonedDateTime.now(),
                    List.of(new OrderPaidEvent.Item(1L, "상품", 2, 1_000L, 2_000L))
                ))
            );
            outboxRepository.save(outbox);
            return outbox;
        }

        private String serialize(OrderPaidEvent event) {
            try {
                return objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class FakeOrderEventOutboxRepository implements OrderEventOutboxRepository {
        private final List<OrderEventOutbox> outboxes = new ArrayList<>();
        private int lockedQueryCallCount = 0;

        @Override
        public OrderEventOutbox save(OrderEventOutbox outbox) {
            if (!outboxes.contains(outbox)) {
                outboxes.add(outbox);
            }
            return outbox;
        }

        @Override
        public List<OrderEventOutbox> findPendingEvents() {
            return outboxes.stream()
                .filter(OrderEventOutbox::isPending)
                .toList();
        }

        @Override
        public List<OrderEventOutbox> findPendingEventsForUpdate(int limit) {
            lockedQueryCallCount++;
            return findPendingEvents();
        }
    }

    private static class FakeDataPlatformClient implements DataPlatformClient {
        private DataPlatformResult result = DataPlatformResult.success();
        private final List<OrderPaidEvent> sentEvents = new ArrayList<>();

        @Override
        public DataPlatformResult sendOrderPaid(OrderPaidEvent event) {
            sentEvents.add(event);
            return result;
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
