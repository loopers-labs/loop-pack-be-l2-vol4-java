package com.loopers.domain.ranking;

import java.util.Optional;

/**
 * 랭킹 점수에 반영되는 유저 행동 신호의 종류. 랭킹 컨텍스트가 스스로 정의하는 어휘로,
 * metrics 의 CatalogEventType 에 얽매이지 않는다(바운디드 컨텍스트 독립).
 */
public enum RankingSignal {
    VIEW,
    LIKE,
    UNLIKE,
    ORDER;

    /**
     * catalog-events 의 원시 eventType 을 랭킹 신호로 번역한다(anti-corruption).
     * 랭킹 점수와 무관한 종류(재고 변경 등)는 빈 값으로 걸러진다.
     */
    public static Optional<RankingSignal> fromCatalogEventType(String rawEventType) {
        if (rawEventType == null) {
            return Optional.empty();
        }
        return switch (rawEventType) {
            case "VIEWED" -> Optional.of(VIEW);
            case "LIKED" -> Optional.of(LIKE);
            case "UNLIKED" -> Optional.of(UNLIKE);
            default -> Optional.empty();
        };
    }
}
