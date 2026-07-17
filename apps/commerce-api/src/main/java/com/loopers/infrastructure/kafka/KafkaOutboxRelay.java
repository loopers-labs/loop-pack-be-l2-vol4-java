package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestedEvent;
import com.loopers.domain.like.LikeChangedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.product.ProductViewedEvent;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaOutboxRelay {

    public static final String CATALOG_TOPIC = "catalog-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCreatedEvent event) {
        Optional<OutboxEntity> outboxOpt = outboxJpaRepository.findTopByPartitionKeyAndTopicAndStatus(
            String.valueOf(event.orderId()), OutboxService.ORDER_EVENTS_TOPIC, OutboxStatus.PENDING
        );

        if (outboxOpt.isEmpty()) {
            log.warn("[Outbox] outbox record not found for orderId={}", event.orderId());
            return;
        }

        OutboxEntity record = outboxOpt.get();
        try {
            OrderEventMessage message = objectMapper.readValue(record.getPayload(), OrderEventMessage.class);
            kafkaTemplate.send(record.getTopic(), record.getPartitionKey(), message);
            record.markSent();
            outboxJpaRepository.save(record);
            log.info("[Outbox] sent order event orderId={}", event.orderId());
        } catch (Exception e) {
            log.warn("[Outbox] kafka send failed for orderId={}, scheduler will retry", event.orderId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductViewedEvent event) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventMessage message = new CatalogEventMessage(eventId, "PRODUCT_VIEWED", event.productId(), ZonedDateTime.now());
        try {
			kafkaTemplate.send(CATALOG_TOPIC, String.valueOf(event.productId()), message);
            log.debug("[Kafka] sent product viewed event productId={}", event.productId());
        } catch (Exception e) {
            log.warn("[Kafka] send failed for productId={}", event.productId(), e);
        }
    }

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void handle(CouponIssueRequestedEvent event) {
		Optional<OutboxEntity> outboxOpt = outboxJpaRepository.findByEventId(event.eventId());

		if (outboxOpt.isEmpty()) {
			log.warn("[Outbox] outbox record not found for eventId={}", event.eventId());
			return;
		}

		OutboxEntity record = outboxOpt.get();
		try {
			CouponIssueRequestMessage message = objectMapper.readValue(record.getPayload(), CouponIssueRequestMessage.class);
			kafkaTemplate.send(record.getTopic(), record.getPartitionKey(), message);
			record.markSent();
			outboxJpaRepository.save(record);
			log.info("[Outbox] sent coupon issue request couponId={}, userId={}", event.couponId(), event.userId());
		} catch (Exception e) {
			log.warn("[Outbox] kafka send failed for couponId={}, scheduler will retry", event.couponId(), e);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void handle(LikeChangedEvent event) {
		Optional<OutboxEntity> outboxOpt = outboxJpaRepository.findByEventId(event.eventId());

		if (outboxOpt.isEmpty()) {
			log.warn("[Outbox] outbox record not found for eventId={}", event.eventId());
			return;
		}

		OutboxEntity record = outboxOpt.get();
		try {
			LikeEventMessage message = objectMapper.readValue(record.getPayload(), LikeEventMessage.class);
			kafkaTemplate.send(record.getTopic(), record.getPartitionKey(), message);
			record.markSent();
			outboxJpaRepository.save(record);
			log.info("[Outbox] sent like event productId={}, type={}", event.productId(), event.type());
		} catch (Exception e) {
			log.warn("[Outbox] kafka send failed for productId={}, scheduler will retry", event.productId(), e);
		}
	}
}
