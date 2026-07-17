package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.kafka.message.CatalogEventMessage;
import com.loopers.infrastructure.kafka.message.CouponIssueRequestMessage;
import com.loopers.infrastructure.kafka.message.LikeEventMessage;
import com.loopers.infrastructure.kafka.message.OrderEventMessage;
import com.loopers.infrastructure.outbox.OutboxEntity;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxService;
import com.loopers.infrastructure.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void retryPending() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(1);
        List<OutboxEntity> pending = outboxJpaRepository.findByStatusAndCreatedAtBefore(OutboxStatus.PENDING, threshold);

        for (OutboxEntity record : pending) {
            try {
                Object message = deserialize(record);
                kafkaTemplate.send(record.getTopic(), record.getPartitionKey(), message);
                record.markSent();
                outboxJpaRepository.save(record);
                log.info("[OutboxScheduler] resent record id={} topic={}", record.getId(), record.getTopic());
            } catch (Exception e) {
                log.error("[OutboxScheduler] failed to relay record id={}", record.getId(), e);
            }
        }
    }

	private Object deserialize(OutboxEntity record) throws Exception {
		if (OutboxService.ORDER_EVENTS_TOPIC.equals(record.getTopic())) {
			return objectMapper.readValue(record.getPayload(), OrderEventMessage.class);
		}
		if (KafkaOutboxRelay.CATALOG_TOPIC.equals(record.getTopic())) {
			return objectMapper.readValue(record.getPayload(), CatalogEventMessage.class);
		}
		if (OutboxService.COUPON_ISSUE_REQUESTS_TOPIC.equals(record.getTopic())) {
			return objectMapper.readValue(record.getPayload(), CouponIssueRequestMessage.class);
		}
		if (OutboxService.LIKE_EVENTS_TOPIC.equals(record.getTopic())) {
			return objectMapper.readValue(record.getPayload(), LikeEventMessage.class);
		}
		throw new IllegalArgumentException("Unknown topic: " + record.getTopic());
	}
}
