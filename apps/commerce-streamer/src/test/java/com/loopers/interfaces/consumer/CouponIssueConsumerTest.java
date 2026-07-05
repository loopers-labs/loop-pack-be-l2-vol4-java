package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.coupon.CouponIssueRequestMessage;
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

class CouponIssueConsumerTest {

    private final CouponIssueProcessor processor = mock(CouponIssueProcessor.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final CouponIssueConsumer consumer = new CouponIssueConsumer(new ObjectMapper(), processor);

    private ConsumerRecord<String, byte[]> rec(String value) {
        return new ConsumerRecord<>("coupon-issue-requests", 0, 0L, "10", value.getBytes(StandardCharsets.UTF_8));
    }

    private static final String OK =
        "{\"requestId\":\"req-1\",\"couponId\":10,\"userId\":7,\"occurredAt\":\"t\"}";

    @DisplayName("정상 메시지는 처리하고 ack 한다.")
    @Test
    void processesAndAcks() {
        consumer.consume(List.of(rec(OK)), ack);
        verify(processor).handle(any(CouponIssueRequestMessage.class));
        verify(ack).acknowledge();
    }

    @DisplayName("파싱 불가는 BatchListenerFailedException 으로 전파되어(→ 재시도/DLT) ack 하지 않는다.")
    @Test
    void routesUnparseableToDlt() {
        assertThrows(BatchListenerFailedException.class, () -> consumer.consume(List.of(rec("not-json")), ack));
        verify(processor, never()).handle(any());
        verify(ack, never()).acknowledge();
    }

    @DisplayName("처리 실패는 BatchListenerFailedException 으로 전파되어 ack 하지 않는다(재시도/DLT).")
    @Test
    void propagatesProcessingFailureWithoutAck() {
        doThrow(new RuntimeException("db down")).when(processor).handle(any());
        assertThrows(BatchListenerFailedException.class, () -> consumer.consume(List.of(rec(OK)), ack));
        verify(ack, never()).acknowledge();
    }
}
