package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.MetricsProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CatalogEventConsumerTest {

    private final MetricsProcessor processor = mock(MetricsProcessor.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final CatalogEventConsumer consumer = new CatalogEventConsumer(new ObjectMapper(), processor);

    private ConsumerRecord<String, byte[]> rec(String value) {
        return new ConsumerRecord<>("catalog-events", 0, 0L, "1", value.getBytes(StandardCharsets.UTF_8));
    }

    private static final String OK =
        "{\"eventId\":\"e1\",\"type\":\"LikeAdded\",\"productId\":100,\"likeCount\":5,\"version\":2,\"occurredAt\":\"t\"}";

    @DisplayName("정상 메시지는 처리하고 ack 한다.")
    @Test
    void processesAndAcks() {
        consumer.consume(List.of(rec(OK)), ack);
        verify(processor).handleCatalog(any(CatalogEventMessage.class));
        verify(ack).acknowledge();
    }

    @DisplayName("파싱 불가 레코드는 BatchListenerFailedException 으로 전파되어(→ 재시도/DLT) ack 하지 않는다.")
    @Test
    void routesUnparseableToDlt() {
        assertThrows(BatchListenerFailedException.class, () -> consumer.consume(List.of(rec("not-json")), ack));
        verify(processor, never()).handleCatalog(any());
        verify(ack, never()).acknowledge();
    }

    @DisplayName("처리 중 예외는 BatchListenerFailedException 으로 전파되어 ack 하지 않는다(재시도/DLT 유도).")
    @Test
    void propagatesProcessingFailureWithoutAck() {
        doThrow(new RuntimeException("db down")).when(processor).handleCatalog(any());
        assertThrows(BatchListenerFailedException.class, () -> consumer.consume(List.of(rec(OK)), ack));
        verify(ack, never()).acknowledge();
    }
}
