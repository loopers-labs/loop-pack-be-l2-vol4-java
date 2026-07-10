package com.loopers.infrastructure.kafka;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// outbox 이벤트를 Kafka 토픽으로 발행하고, 브로커 ack 확인 후에만 markDoneIfPending으로 완료 처리한다.
// 발행 확인 전에 완료 처리하면 전송 실패 시에도 outbox가 DONE이 되어 유실될 수 있어 순서를 지킨다.
// 여러 OutboxEventHandler(좋아요/주문 등)가 이 순서를 공유한다.
@RequiredArgsConstructor
@Component
public class KafkaOutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final KafkaEventEnvelopeSerializer envelopeSerializer;
    private final OutboxService outboxService;

    public void publishAndMarkDone(OutboxModel outbox, String topic) {
        String envelope = envelopeSerializer.serialize(outbox);
        try {
            stringKafkaTemplate.send(topic, outbox.getAggregateId(), envelope).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "kafka 발행이 중단되었습니다.");
        } catch (ExecutionException | TimeoutException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "kafka 발행에 실패했습니다.");
        }
        outboxService.markDoneIfPending(outbox.getEventId());
    }
}
