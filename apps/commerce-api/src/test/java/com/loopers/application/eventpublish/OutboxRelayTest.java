package com.loopers.application.eventpublish;

import com.loopers.domain.event.KafkaTopics;
import com.loopers.domain.eventpublish.FakeOutboxRepository;
import com.loopers.domain.eventpublish.OutboxMessage;
import com.loopers.domain.eventpublish.OutboxService;
import com.loopers.domain.eventpublish.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayTest {

    private FakeOutboxRepository repo;
    private OutboxService service;
    private RecordingOutboxRelayPort port;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repo = new FakeOutboxRepository();
        service = new OutboxService(repo);
        port = new RecordingOutboxRelayPort();
        relay = new OutboxRelay(service, port);
        ReflectionTestUtils.setField(relay, "batchSize", 100);
    }

    private OutboxMessage make(String key) {
        return OutboxMessage.create(
            "Product", key, "LikeChanged",
            KafkaTopics.CATALOG_EVENTS, key, "{\"productId\":" + key + "}"
        );
    }

    @DisplayName("정상 흐름")
    @Nested
    class HappyPath {

        @DisplayName("PENDING 메시지들을 발행 후 모두 SENT 로 마킹한다.")
        @Test
        void publishesAndMarksSent() {
            service.append(make("1"));
            service.append(make("2"));

            relay.relayPending();

            assertThat(port.published).hasSize(2);
            assertThat(service.loadPendingBatch(100)).isEmpty();
            assertThat(repo.findById(1L).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @DisplayName("PENDING 이 없으면 발행 없이 즉시 종료한다.")
        @Test
        void nothingToDo() {
            relay.relayPending();

            assertThat(port.published).isEmpty();
        }
    }

    @DisplayName("발행 실패")
    @Nested
    class Failure {

        @DisplayName("일부 메시지 발행 실패 시, 실패건은 PENDING 유지 + retry 증가, 성공건은 SENT.")
        @Test
        void partialFailure() {
            OutboxMessage first = service.append(make("1"));
            OutboxMessage second = service.append(make("2"));
            port.failFor(second.getPartitionKey());

            relay.relayPending();

            assertThat(repo.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(repo.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(repo.findById(second.getId()).orElseThrow().getRetryCount()).isEqualTo(1);
        }

        @DisplayName("재폴링 시 실패 메시지가 다시 시도된다.")
        @Test
        void retryOnNextPoll() {
            OutboxMessage m = service.append(make("1"));
            port.failFor(m.getPartitionKey());

            relay.relayPending();
            port.clearFailures();
            relay.relayPending();

            assertThat(repo.findById(m.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }

    static class RecordingOutboxRelayPort implements OutboxRelayPort {
        final List<OutboxMessage> published = new ArrayList<>();
        final List<String> failKeys = new ArrayList<>();

        void failFor(String partitionKey) {
            failKeys.add(partitionKey);
        }

        void clearFailures() {
            failKeys.clear();
        }

        @Override
        public void publish(OutboxMessage message) {
            if (failKeys.contains(message.getPartitionKey())) {
                throw new IllegalStateException("simulated broker down");
            }
            published.add(message);
        }
    }
}
