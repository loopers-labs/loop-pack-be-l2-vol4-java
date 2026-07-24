package com.loopers.domain.ranking;

import java.math.BigDecimal;

public class RankingCommand {

    public record UpdateRanking(Long productId, EventType type, Long quantity, BigDecimal price) {

        public enum EventType {
            VIEW, LIKE, UNLIKE, ORDER
        }

        public static UpdateRanking view(Long productId) {
            return new UpdateRanking(productId, EventType.VIEW, null, null);
        }

        public static UpdateRanking like(Long productId) {
            return new UpdateRanking(productId, EventType.LIKE, null, null);
        }

        public static UpdateRanking unlike(Long productId) {
            return new UpdateRanking(productId, EventType.UNLIKE, null, null);
        }

        public static UpdateRanking order(Long productId, Long quantity, BigDecimal price) {
            return new UpdateRanking(productId, EventType.ORDER, quantity, price);
        }
    }
}
