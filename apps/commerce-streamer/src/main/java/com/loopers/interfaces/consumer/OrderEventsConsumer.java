package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.handled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
public class OrderEventsConsumer {

    private static final String CONSUMER_GROUP = "order-metrics-consumer";
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final ProductMetricsService productMetricsService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OrderEventsConsumer(
            ProductMetricsService productMetricsService,
            EventHandledRepository eventHandledRepository,
            ObjectMapper objectMapper
    ) {
        this(productMetricsService, eventHandledRepository, objectMapper, Clock.system(ZONE_SEOUL));
    }

    OrderEventsConsumer(
            ProductMetricsService productMetricsService,
            EventHandledRepository eventHandledRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.productMetricsService = productMetricsService;
        this.eventHandledRepository = eventHandledRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${commerce-streamer.kafka.topics.order-events:order-events}",
            groupId = CONSUMER_GROUP,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    @Transactional
    public void consumeOrderEvents(
            List<ConsumerRecord<Object, Object>> messages,
            Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<Object, Object> record : messages) {
            try {
                OutboxEventPayload payload = OutboxEventPayload.from(record.value(), objectMapper);
                if (!eventHandledRepository.markIfNotHandled(payload.eventId(), CONSUMER_GROUP)) {
                    continue;
                }
                processEvent(payload);
            } catch (Exception e) {
                log.error("order-events 처리 실패 [offset={}]", record.offset(), e);
                throw new IllegalStateException("order-events 처리 실패", e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processEvent(OutboxEventPayload payload) {
        if (!"PaymentCompleteEvent".equals(payload.eventType())) {
            return;
        }

        String orderId = payload.data().path("orderId").asText(null);
        if (orderId == null) {
            log.warn("orderId 없는 PaymentCompleteEvent 무시 [eventId={}]", payload.eventId());
            return;
        }

        productMetricsService.recordPurchase(payload.eventId(), orderId, LocalDate.now(clock));
    }
}
