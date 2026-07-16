package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RankingScorePolicy {
    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    public void validate(CatalogEventMessage event) {
        if (event == null) {
            throw new IllegalArgumentException("랭킹 이벤트는 null일 수 없습니다.");
        }
        if (event.eventType() == null) {
            throw new IllegalArgumentException("랭킹 이벤트의 eventType은 필수입니다.");
        }
        if (!isRankingEvent(event.eventType())) {
            return;
        }
        if (event.productId() == null) {
            throw new IllegalArgumentException("랭킹 이벤트의 productId는 필수입니다.");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("랭킹 이벤트의 occurredAt은 필수입니다.");
        }

        String deltaKey = switch (event.eventType()) {
            case "PRODUCT_VIEWED" -> "viewCountDelta";
            case "PRODUCT_LIKED", "PRODUCT_UNLIKED" -> "likeCountDelta";
            case "PRODUCT_ORDERED" -> "salesCountDelta";
            default -> throw new IllegalStateException("검증 대상이 아닌 이벤트입니다.");
        };
        validateDelta(event.data(), deltaKey);
    }

    public double score(CatalogEventMessage event) {
        if (event.eventType() == null) {
            return 0.0;
        }
        return switch (event.eventType()) {
            case "PRODUCT_VIEWED" -> VIEW_WEIGHT * event.viewCountDelta();
            case "PRODUCT_LIKED", "PRODUCT_UNLIKED" -> LIKE_WEIGHT * event.likeCountDelta();
            case "PRODUCT_ORDERED" -> ORDER_WEIGHT * event.salesCountDelta();
            default -> 0.0;
        };
    }

    private boolean isRankingEvent(String eventType) {
        return "PRODUCT_VIEWED".equals(eventType)
            || "PRODUCT_LIKED".equals(eventType)
            || "PRODUCT_UNLIKED".equals(eventType)
            || "PRODUCT_ORDERED".equals(eventType);
    }

    private void validateDelta(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key) || data.get(key) == null) {
            throw new IllegalArgumentException("랭킹 이벤트의 " + key + "는 필수입니다.");
        }
        Object value = data.get(key);
        if (value instanceof Number) {
            return;
        }
        if (value instanceof String stringValue) {
            try {
                Integer.parseInt(stringValue);
                return;
            } catch (NumberFormatException ignored) {
                // 아래의 일관된 semantic validation 예외로 변환한다.
            }
        }
        throw new IllegalArgumentException("랭킹 이벤트의 " + key + "는 정수여야 합니다.");
    }
}
