package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingScorePolicyTest {
    private final RankingScorePolicy policy = new RankingScorePolicy();

    @DisplayName("이벤트 종류와 변화량에 가중치를 적용한다.")
    @MethodSource("scores")
    @ParameterizedTest
    void calculatesWeightedScore(String eventType, Map<String, Object> data, double expected) {
        CatalogEventMessage event = new CatalogEventMessage(
            "event", eventType, "PRODUCT", 1L, ZonedDateTime.now(), data
        );

        assertThat(policy.score(event)).isCloseTo(expected, within(0.000_001));
    }

    private static Stream<Arguments> scores() {
        return Stream.of(
            Arguments.of("PRODUCT_VIEWED", Map.of("viewCountDelta", 1), 0.1),
            Arguments.of("PRODUCT_LIKED", Map.of("likeCountDelta", 1), 0.2),
            Arguments.of("PRODUCT_UNLIKED", Map.of("likeCountDelta", -1), -0.2),
            Arguments.of("PRODUCT_ORDERED", Map.of("salesCountDelta", 2), 1.4),
            Arguments.of("UNKNOWN", Map.of(), 0.0)
        );
    }

    @DisplayName("주문 1건의 점수는 좋아요 3건의 점수보다 높다.")
    @Test
    void givesOrderMoreScoreThanThreeLikes() {
        CatalogEventMessage order = new CatalogEventMessage(
            "order", "PRODUCT_ORDERED", "PRODUCT", 1L, ZonedDateTime.now(), Map.of("salesCountDelta", 1)
        );
        CatalogEventMessage likes = new CatalogEventMessage(
            "likes", "PRODUCT_LIKED", "PRODUCT", 1L, ZonedDateTime.now(), Map.of("likeCountDelta", 3)
        );

        assertThat(policy.score(order)).isGreaterThan(policy.score(likes));
    }

    @DisplayName("랭킹 이벤트에 필요한 변화량이 없거나 파싱할 수 없으면 semantic validation에 실패한다.")
    @Test
    void rejectsMissingOrInvalidDelta() {
        CatalogEventMessage missingDelta = new CatalogEventMessage(
            "view", "PRODUCT_VIEWED", "PRODUCT", 1L, ZonedDateTime.now(), Map.of()
        );
        CatalogEventMessage invalidDelta = new CatalogEventMessage(
            "order", "PRODUCT_ORDERED", "PRODUCT", 1L, ZonedDateTime.now(), Map.of("salesCountDelta", "one")
        );

        assertThrows(IllegalArgumentException.class, () -> policy.validate(missingDelta));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(invalidDelta));
    }

    @DisplayName("랭킹 대상 이벤트의 상품과 발생 시각이 없으면 semantic validation에 실패한다.")
    @Test
    void rejectsMissingProductOrOccurredAt() {
        CatalogEventMessage missingProduct = new CatalogEventMessage(
            "view", "PRODUCT_VIEWED", "PRODUCT", null, ZonedDateTime.now(), Map.of("viewCountDelta", 1)
        );
        CatalogEventMessage missingOccurredAt = new CatalogEventMessage(
            "view", "PRODUCT_VIEWED", "PRODUCT", 1L, null, Map.of("viewCountDelta", 1)
        );
        CatalogEventMessage missingType = new CatalogEventMessage(
            "view", null, "PRODUCT", 1L, ZonedDateTime.now(), Map.of("viewCountDelta", 1)
        );

        assertThrows(IllegalArgumentException.class, () -> policy.validate(missingProduct));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(missingOccurredAt));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(missingType));
    }
}
