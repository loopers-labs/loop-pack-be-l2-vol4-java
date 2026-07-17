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
 * order-events 를 소비해 주문(수량)을 랭킹 ZSET 에 반영한다. metrics 컨슈머와 별개 그룹(ranking-order).
 * paidAt 으로 날짜를 버킷팅하고, 라인마다 수량을 delta 로 넣는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingOrderConsumer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingService rankingService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopic.ORDER_EVENTS,
            groupId = "ranking-order",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        LocalDate today = LocalDate.now(SEOUL);
        List<RankingEvent> events = new ArrayList<>();

        for (ConsumerRecord<String, byte[]> record : records) {
            OrderPaidMessage message = deserialize(record);
            if (message == null) {
                continue;
            }
            RankingWindow.Classification classification = RankingWindow.classify(message.paidAt(), today);
            switch (classification.verdict()) {
                case IN_WINDOW -> {
                    for (OrderPaidMessage.Line line : message.items()) {
                        events.add(new RankingEvent(classification.date(), line.productId(), RankingSignal.ORDER, line.quantity()));
                    }
                }
                case FUTURE -> deadLetterPublisher.publish(record, new IllegalStateException("ranking future paidAt=" + message.paidAt()));
                case TOO_OLD -> log.warn("ranking drop reason=too_old paidAt={}", message.paidAt());
            }
        }

        rankingService.apply(events);
        acknowledgment.acknowledge();
    }

    private OrderPaidMessage deserialize(ConsumerRecord<String, byte[]> record) {
        try {
            return objectMapper.readValue(record.value(), OrderPaidMessage.class);
        } catch (IOException e) {
            deadLetterPublisher.publish(record, e);
            return null;
        }
    }
}
