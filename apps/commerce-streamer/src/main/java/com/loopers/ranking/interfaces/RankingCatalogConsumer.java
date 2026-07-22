package com.loopers.ranking.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.ranking.application.RankingEvent;
import com.loopers.ranking.application.RankingService;
import com.loopers.ranking.domain.RankingSignal;
import com.loopers.ranking.domain.RankingWindow;
import com.loopers.support.dlq.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * catalog-events 를 소비해 조회/좋아요를 랭킹 ZSET 에 반영한다. metrics 컨슈머와 별개 그룹(ranking-catalog).
 * 역직렬화 실패·미래 시각은 DLT 로 격리하고, 너무 과거는 드롭하며, 나머지를 fold 하여 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCatalogConsumer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingService rankingService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopic.CATALOG_EVENTS,
            groupId = "ranking-catalog",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        LocalDate today = LocalDate.now(SEOUL);
        List<RankingEvent> events = new ArrayList<>();

        for (ConsumerRecord<String, byte[]> record : records) {
            CatalogEventMessage message = deserialize(record);
            if (message == null) {
                continue;
            }
            RankingSignal signal = signalOf(message.type());
            RankingWindow.Classification classification = RankingWindow.classify(message.occurredAt(), today);
            switch (classification.verdict()) {
                case IN_WINDOW -> events.add(new RankingEvent(classification.date(), message.productId(), signal, message.delta()));
                case FUTURE -> deadLetterPublisher.publish(record, new IllegalStateException("ranking future occurredAt=" + message.occurredAt()));
                case TOO_OLD -> log.warn("ranking drop reason=too_old productId={} occurredAt={}", message.productId(), message.occurredAt());
            }
        }

        rankingService.apply(events);
        acknowledgment.acknowledge();
    }

    private CatalogEventMessage deserialize(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), CatalogEventMessage.class);
        } catch (IOException e) {
            deadLetterPublisher.publish(record, e);
            return null;
        }
    }

    private RankingSignal signalOf(CatalogEventType type) {
        return switch (type) {
            case VIEW -> RankingSignal.VIEW;
            case LIKE -> RankingSignal.LIKE;
        };
    }
}
