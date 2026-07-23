package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.ranking.CatalogRankingEventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogRankingEventConsumerTest {
    @DisplayName("배치 전체 처리가 성공한 뒤 offset을 acknowledge한다.")
    @Test
    void acknowledgesAfterBatchProcessing() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CatalogRankingEventProcessor processor = mock(CatalogRankingEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CatalogRankingEventConsumer consumer = new CatalogRankingEventConsumer(objectMapper, processor);
        String payload = """
            {"eventId":"e1","eventType":"PRODUCT_VIEWED","aggregateType":"PRODUCT","aggregateId":1,
             "occurredAt":"2026-07-17T09:00:00+09:00","data":{"viewCountDelta":1}}
            """;

        consumer.consume(List.of(new ConsumerRecord<>("catalog-events", 0, 0L, "1", payload)), acknowledgment);

        verify(processor).process(anyList());
        verify(acknowledgment).acknowledge();
    }

    @DisplayName("역직렬화에 실패하면 offset을 acknowledge하지 않는다.")
    @Test
    void doesNotAcknowledgeWhenDeserializationFails() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CatalogRankingEventProcessor processor = mock(CatalogRankingEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CatalogRankingEventConsumer consumer = new CatalogRankingEventConsumer(objectMapper, processor);

        assertThrows(IOException.class, () ->
            consumer.consume(List.of(new ConsumerRecord<>("catalog-events", 0, 0L, "1", "not-json")), acknowledgment)
        );

        verify(processor, never()).process(anyList());
        verify(acknowledgment, never()).acknowledge();
    }

    @DisplayName("랭킹 배치 처리에 실패하면 offset을 acknowledge하지 않는다.")
    @Test
    void doesNotAcknowledgeWhenProcessorFails() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CatalogRankingEventProcessor processor = mock(CatalogRankingEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CatalogRankingEventConsumer consumer = new CatalogRankingEventConsumer(objectMapper, processor);
        String payload = """
            {"eventId":"e1","eventType":"PRODUCT_VIEWED","aggregateType":"PRODUCT","aggregateId":1,
             "occurredAt":"2026-07-17T09:00:00+09:00","data":{"viewCountDelta":1}}
            """;
        doThrow(new IllegalArgumentException("invalid event")).when(processor).process(anyList());

        assertThrows(IllegalArgumentException.class, () ->
            consumer.consume(List.of(new ConsumerRecord<>("catalog-events", 0, 0L, "1", payload)), acknowledgment)
        );

        verify(acknowledgment, never()).acknowledge();
    }
}
