package com.loopers.domain.product.event;

import java.time.ZonedDateTime;

public record ProductViewed(Long productId, Long userId, ZonedDateTime occurredAt) {}
