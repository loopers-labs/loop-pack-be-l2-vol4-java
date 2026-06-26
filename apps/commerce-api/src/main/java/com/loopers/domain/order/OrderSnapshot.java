package com.loopers.domain.order;

import java.util.List;

public record OrderSnapshot(
        List<OrderSnapshotItem> items,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        String couponId
) {}
