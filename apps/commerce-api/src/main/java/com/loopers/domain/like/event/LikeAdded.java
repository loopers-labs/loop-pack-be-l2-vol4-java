package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

public record LikeAdded(Long userId, Long productId, ZonedDateTime occurredAt) {}
