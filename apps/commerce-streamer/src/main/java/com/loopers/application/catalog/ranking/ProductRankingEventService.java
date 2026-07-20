package com.loopers.application.catalog.ranking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.catalog.ranking.RankingRepository;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.kafka.event.EventMessage;
import com.loopers.kafka.event.ProductLikeEventPayload;
import com.loopers.kafka.event.ProductViewEventPayload;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ProductRankingEventService {

    private static final String TOPIC_CATALOG_EVENTS = "catalog-events";
    private static final String TOPIC_ORDER_EVENTS = "order-events";
    private static final String EVENT_PRODUCT_VIEWED = "PRODUCT_VIEWED";
    private static final String EVENT_PRODUCT_LIKED = "PRODUCT_LIKED";
    private static final String EVENT_PRODUCT_UNLIKED = "PRODUCT_UNLIKED";
    private static final String EVENT_ORDER_PAID = "ORDER_PAID";
    private static final String HANDLED_ID_PREFIX = "ranking:";
    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration RANKING_TTL = Duration.ofDays(2);
    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        EVENT_PRODUCT_VIEWED,
        EVENT_PRODUCT_LIKED,
        EVENT_PRODUCT_UNLIKED,
        EVENT_ORDER_PAID
    );

    private final RankingRepository rankingRepository;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    @Transactional
    public ProcessResult process(String topic, EventMessage message) {
        validateMessage(topic, message);
        String handledId = handledId(message.eventId());
        if (eventHandledRepository.exists(handledId)) {
            eventMetrics.recordKafkaConsumerDuplicate(topic, message.eventType());
            return ProcessResult.DUPLICATE;
        }

        if (!SUPPORTED_EVENT_TYPES.contains(message.eventType())) {
            saveHandled(handledId, topic, message);
            eventMetrics.recordKafkaConsumerSuccess(topic, message.eventType());
            return ProcessResult.IGNORED;
        }

        List<ScoreCommand> commands = toScoreCommands(topic, message);
        if (commands.isEmpty()) {
            saveHandled(handledId, topic, message);
            eventMetrics.recordKafkaConsumerSuccess(topic, message.eventType());
            return ProcessResult.IGNORED;
        }

        LocalDate rankingDate = message.occurredAt().withZoneSameInstant(RANKING_ZONE).toLocalDate();
        boolean updated = rankingRepository.incrementScoresOnce(
            rankingDate,
            handledId,
            commands.stream()
                .map(command -> new RankingRepository.Score(command.productId(), command.score()))
                .toList(),
            RANKING_TTL
        );
        if (!updated) {
            eventMetrics.recordKafkaConsumerDuplicate(topic, message.eventType());
            return ProcessResult.DUPLICATE;
        }

        saveHandled(handledId, topic, message);
        eventMetrics.recordKafkaConsumerSuccess(topic, message.eventType());
        return ProcessResult.UPDATED;
    }

    private void saveHandled(String handledId, String topic, EventMessage message) {
        eventHandledRepository.save(new EventHandled(
            handledId,
            topic,
            message.eventType(),
            message.aggregateType(),
            message.aggregateId(),
            ZonedDateTime.now()
        ));
    }

    private List<ScoreCommand> toScoreCommands(String topic, EventMessage message) {
        return switch (message.eventType()) {
            case EVENT_PRODUCT_VIEWED -> {
                ProductViewEventPayload payload = deserialize(topic, message, ProductViewEventPayload.class);
                yield List.of(new ScoreCommand(payload.productId(), VIEW_WEIGHT));
            }
            case EVENT_PRODUCT_LIKED -> {
                ProductLikeEventPayload payload = deserialize(topic, message, ProductLikeEventPayload.class);
                yield List.of(new ScoreCommand(payload.productId(), LIKE_WEIGHT));
            }
            case EVENT_PRODUCT_UNLIKED -> {
                ProductLikeEventPayload payload = deserialize(topic, message, ProductLikeEventPayload.class);
                yield List.of(new ScoreCommand(payload.productId(), -LIKE_WEIGHT));
            }
            case EVENT_ORDER_PAID -> orderScoreCommands(topic, message);
            default -> List.of();
        };
    }

    private List<ScoreCommand> orderScoreCommands(String topic, EventMessage message) {
        OrderPaidRankingPayload payload = deserialize(topic, message, OrderPaidRankingPayload.class);
        if (payload.items() == null || payload.items().isEmpty()) {
            return List.of();
        }

        List<ScoreCommand> commands = new ArrayList<>();
        for (OrderPaidRankingPayload.Item item : payload.items()) {
            if (item.productId() == null) {
                continue;
            }
            long scoreBaseAmount = item.scoreBaseAmount();
            if (scoreBaseAmount <= 0) {
                continue;
            }
            commands.add(new ScoreCommand(item.productId(), scoreBaseAmount * ORDER_WEIGHT));
        }
        return commands;
    }

    private <T> T deserialize(String topic, EventMessage message, Class<T> payloadType) {
        try {
            return objectMapper.readValue(message.payload(), payloadType);
        } catch (JsonProcessingException e) {
            eventMetrics.recordKafkaConsumerFailure(topic, message.eventType());
            throw new IllegalArgumentException("랭킹 이벤트 payload 해석에 실패했습니다.", e);
        }
    }

    private void validateMessage(String topic, EventMessage message) {
        if (message == null || isBlank(topic) || isBlank(message.eventId()) || isBlank(message.eventType())) {
            eventMetrics.recordKafkaConsumerFailure(topic == null ? "UNKNOWN" : topic, "UNKNOWN");
            throw new IllegalArgumentException("랭킹 이벤트 메시지는 필수입니다.");
        }
        if (isBlank(message.aggregateType()) || isBlank(message.aggregateId()) || isBlank(message.payload())
            || message.occurredAt() == null) {
            eventMetrics.recordKafkaConsumerFailure(topic, message.eventType());
            throw new IllegalArgumentException("랭킹 이벤트 메시지의 aggregate, payload, occurredAt은 필수입니다.");
        }
        if (!TOPIC_CATALOG_EVENTS.equals(topic) && !TOPIC_ORDER_EVENTS.equals(topic)) {
            eventMetrics.recordKafkaConsumerFailure(topic, message.eventType());
            throw new IllegalArgumentException("랭킹 이벤트 topic이 아닙니다.");
        }
    }

    private String handledId(String eventId) {
        return HANDLED_ID_PREFIX + eventId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ScoreCommand(Long productId, double score) {
    }

    public enum ProcessResult {
        UPDATED,
        DUPLICATE,
        IGNORED
    }
}
