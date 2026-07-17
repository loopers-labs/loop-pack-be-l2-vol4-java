package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.ranking.RankingScorePolicy;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.interfaces.consumer.message.LikeEventMessage;
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
public class LikeEventConsumer {

    private final ObjectMapper objectMapper;
    private final EventHandledJpaRepository eventHandledRepository;
    private final ProductMetricsJpaRepository productMetricsRepository;
    private final RankingScorePolicy rankingScorePolicy;
    private final RankingRedisRepository rankingRedisRepository;

    @KafkaListener(topics = "like-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                LikeEventMessage message = objectMapper.readValue((byte[]) record.value(), LikeEventMessage.class);
                if (eventHandledRepository.existsById(message.eventId())) {
                    log.debug("[LikeConsumer] skipped duplicate eventId={}", message.eventId());
                    continue;
                }
                processEvent(message);
                eventHandledRepository.save(EventHandledEntity.of(message.eventId()));
            } catch (Exception e) {
                log.error("[LikeConsumer] failed to process record offset={}", record.offset(), e);
            }
        }
        ack.acknowledge();
    }

    private void processEvent(LikeEventMessage message) {
        if ("PRODUCT_LIKED".equals(message.type())) {
            productMetricsRepository.upsert(message.productId(), 0L, 0L, 1L);
            rankingRedisRepository.addScore(message.productId(), rankingScorePolicy.likeScore(), message.occurredAt());
            log.info("[LikeConsumer] like_count+1 productId={}", message.productId());
        } else if ("PRODUCT_UNLIKED".equals(message.type())) {
            productMetricsRepository.upsert(message.productId(), 0L, 0L, -1L);
            rankingRedisRepository.addScore(message.productId(), -rankingScorePolicy.likeScore(), message.occurredAt());
            log.info("[LikeConsumer] like_count-1 productId={}", message.productId());
        }
    }
}
