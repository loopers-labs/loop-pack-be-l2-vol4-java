package com.loopers.ranking.interfaces;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.ranking.application.RankingEvent;
import com.loopers.ranking.application.RankingService;
import com.loopers.support.dlq.DeadLetterPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 발생시각이 없는 메시지는 DLT 로 보내고 배치는 계속 진행한다.
 * 예외가 consume() 밖으로 나가면 ack 에 도달하지 못해 같은 배치가 무한 재전달되고 파티션이 멈춘다.
 */
class RankingConsumerUndatedTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final RankingService rankingService = mock(RankingService.class);
    private final DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

    private ConsumerRecord<String, byte[]> record(String topic, String json) {
        return new ConsumerRecord<>(topic, 0, 0L, "k", json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("occurredAt 이 없는 catalog 메시지는 DLT 로 가고 ack 된다")
    void givenCatalogMessageWithoutOccurredAt_whenConsume_thenDeadLetteredAndAcked() {
        RankingCatalogConsumer consumer =
                new RankingCatalogConsumer(rankingService, deadLetterPublisher, objectMapper);
        String json = """
                {"productId":100,"type":"VIEW","delta":1}
                """;

        consumer.consume(List.of(record(KafkaTopic.CATALOG_EVENTS, json)), acknowledgment);

        verify(deadLetterPublisher, times(1)).publish(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("paidAt 이 없는 order 메시지는 DLT 로 가고 ack 된다")
    void givenOrderMessageWithoutPaidAt_whenConsume_thenDeadLetteredAndAcked() {
        RankingOrderConsumer consumer =
                new RankingOrderConsumer(rankingService, deadLetterPublisher, objectMapper);
        String json = """
                {"eventId":"e1","orderId":1,"items":[{"productId":100,"quantity":2}]}
                """;

        consumer.consume(List.of(record(KafkaTopic.ORDER_EVENTS, json)), acknowledgment);

        verify(deadLetterPublisher, times(1)).publish(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("날짜 없는 메시지가 섞여 있어도 나머지는 정상 반영된다")
    void givenUndatedAmongValid_whenConsume_thenValidStillApplied() {
        RankingCatalogConsumer consumer =
                new RankingCatalogConsumer(rankingService, deadLetterPublisher, objectMapper);
        String undated = """
                {"productId":100,"type":"VIEW","delta":1}
                """;
        String valid = """
                {"productId":200,"type":"VIEW","delta":1,"occurredAt":"%s"}
                """.formatted(java.time.ZonedDateTime.now());

        consumer.consume(
                List.of(record(KafkaTopic.CATALOG_EVENTS, undated), record(KafkaTopic.CATALOG_EVENTS, valid)),
                acknowledgment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RankingEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(rankingService).apply(captor.capture());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().productId()).isEqualTo(200L);
        verify(deadLetterPublisher, times(1)).publish(any(), any());
    }

    @Test
    @DisplayName("정상 메시지만 있으면 DLT 로 가지 않는다")
    void givenOnlyValidMessages_whenConsume_thenNothingDeadLettered() {
        RankingCatalogConsumer consumer =
                new RankingCatalogConsumer(rankingService, deadLetterPublisher, objectMapper);
        String valid = """
                {"productId":200,"type":"LIKE","delta":1,"occurredAt":"%s"}
                """.formatted(java.time.ZonedDateTime.now());

        consumer.consume(List.of(record(KafkaTopic.CATALOG_EVENTS, valid)), acknowledgment);

        verify(deadLetterPublisher, never()).publish(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }
}
