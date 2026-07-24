package com.loopers.support.dlq;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DLT 발행이 확인된 뒤에야 정상 반환한다. 발행이 실패하면 예외를 던져야 한다 —
 * 그래야 컨슈머가 ack 하지 못하고 원본을 재소비한다(유실 대신 at-least-once).
 * 확인 없이 반환하면 broker 전송 실패 시 원본은 이미 ack 됐고 DLT 에도 없어 메시지가 사라진다.
 */
class DeadLetterPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final DeadLetterPublisher publisher = new DeadLetterPublisher(template);

    private ConsumerRecord<String, byte[]> record() {
        return new ConsumerRecord<>("catalog-events", 0, 0L, "k",
                "payload".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("발행이 성공하면 정상 반환한다")
    void givenSuccessfulSend_whenPublish_thenReturnsNormally() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        assertThatCode(() -> publisher.publish(record(), new RuntimeException("boom")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발행이 실패하면 예외를 던진다 — 원본을 ack 하지 않도록")
    void givenFailedSend_whenPublish_thenThrows() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(template.send(any(ProducerRecord.class))).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish(record(), new RuntimeException("boom")))
                .isInstanceOf(DeadLetterPublishException.class);
    }

    @Test
    @DisplayName("실패 예외 메시지에 원본 토픽을 담아 후속 추적을 돕는다")
    void givenFailedSend_whenPublish_thenExceptionCarriesTopic() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(template.send(any(ProducerRecord.class))).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish(record(), new RuntimeException("boom")))
                .hasMessageContaining("catalog-events");
    }
}
