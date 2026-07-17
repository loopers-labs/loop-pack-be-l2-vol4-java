package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.ranking.RankingScorePolicy;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.interfaces.consumer.message.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final EventHandledJpaRepository eventHandledRepository;
    private final ProductMetricsJpaRepository productMetricsRepository;
    private final RankingScorePolicy rankingScorePolicy;
    private final RankingRedisRepository rankingRedisRepository;

    @KafkaListener(topics = "order-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                OrderEventMessage message = objectMapper.readValue((byte[]) record.value(), OrderEventMessage.class);
                if (eventHandledRepository.existsById(message.eventId())) {
                    log.debug("[OrderConsumer] skipped duplicate eventId={}", message.eventId());
                    continue;
                }
                processEvent(message);
                eventHandledRepository.save(EventHandledEntity.of(message.eventId()));
            } catch (Exception e) {
                log.error("[OrderConsumer] failed to process record offset={}", record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    private void processEvent(OrderEventMessage message) {
        if ("ORDER_CREATED".equals(message.type())) {
            for (OrderEventMessage.Item item : message.items()) {
                productMetricsRepository.upsert(item.productId(), 0L, item.quantity(), 0L);
                double score = rankingScorePolicy.orderScore(item.price(), item.quantity());
                rankingRedisRepository.addScore(item.productId(), score, message.occurredAt());
                log.info("[OrderConsumer] sales_count+{} productId={}", item.quantity(), item.productId());
            }
        }
    }
}
