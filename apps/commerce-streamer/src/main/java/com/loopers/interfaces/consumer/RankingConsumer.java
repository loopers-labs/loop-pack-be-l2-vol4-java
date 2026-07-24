package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.ranking.RankingCommand;
import com.loopers.domain.ranking.RankingService;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// 랭킹 ZSET 갱신 전용 배치 컨슈머. order-events, catalog-events 두 토픽을 구독한다.
// metrics-consumer(DB 적재, 단건 처리)와는 별도 consumer group(ranking-consumer)으로 동일 토픽을 다시 구독한다.
// 배치 단위로 productId별 점수를 메모리에서 합산한 뒤 한 번에 Redis에 반영해 스루풋을 높인다.
// 개별 레코드 파싱 실패는 로그만 남기고 건너뛰며, Redis 반영 실패 시에는 ack를 호출하지 않아
// 배치 전체가 다음 poll에서 재전달되도록 한다(DLQ는 스코프 아님).
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingConsumer {

    private static final String SUPPORTED_ORDER_EVENT_TYPE = "ORDER_CREATED";

    private final RankingService rankingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            id = "rankingOrderConsumer",
            topics = "order-events",
            groupId = "ranking-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void listenOrder(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        List<RankingCommand.UpdateRanking> commands = new ArrayList<>();
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
                if (!SUPPORTED_ORDER_EVENT_TYPE.equals(envelope.eventType())) {
                    log.warn("처리할 수 없는 이벤트 타입입니다. eventType={}", envelope.eventType());
                    continue;
                }
                objectMapper.convertValue(
                        envelope.payload().get("items"), new TypeReference<List<OrderItemPayload>>() {
                        }
                ).forEach(item -> commands.add(RankingCommand.UpdateRanking.order(item.productId(), item.quantity(), item.price())));
            } catch (Exception e) {
                log.error("order-events 랭킹 반영을 위한 파싱 실패. key={}", record.key(), e);
            }
        }
        rankingService.applyBatch(commands);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            id = "rankingCatalogConsumer",
            topics = "catalog-events",
            groupId = "ranking-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void listenCatalog(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        List<RankingCommand.UpdateRanking> commands = new ArrayList<>();
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
                Long productId = Long.valueOf(envelope.aggregateId());
                switch (envelope.eventType()) {
                    case "PRODUCT_LIKED" -> commands.add(RankingCommand.UpdateRanking.like(productId));
                    case "PRODUCT_UNLIKED" -> commands.add(RankingCommand.UpdateRanking.unlike(productId));
                    case "PRODUCT_VIEWED" -> commands.add(RankingCommand.UpdateRanking.view(productId));
                    default -> log.warn("처리할 수 없는 이벤트 타입입니다. eventType={}", envelope.eventType());
                }
            } catch (Exception e) {
                log.error("catalog-events 랭킹 반영을 위한 파싱 실패. key={}", record.key(), e);
            }
        }
        rankingService.applyBatch(commands);
        acknowledgment.acknowledge();
    }

    private record OrderItemPayload(Long productId, Long quantity, BigDecimal price) {
    }
}
