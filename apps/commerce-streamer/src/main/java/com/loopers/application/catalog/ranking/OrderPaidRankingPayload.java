package com.loopers.application.catalog.ranking;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderPaidRankingPayload(
    Long orderId,
    String userId,
    Long originalAmount,
    Long discountAmount,
    Long finalAmount,
    ZonedDateTime paidAt,
    List<Item> items
) {
    public record Item(
        Long productId,
        String productName,
        Integer quantity,
        Long unitPrice,
        Long lineAmount
    ) {
        long scoreBaseAmount() {
            if (lineAmount != null) {
                return lineAmount;
            }
            if (unitPrice == null || quantity == null) {
                return 0L;
            }
            return unitPrice * quantity;
        }
    }
}
