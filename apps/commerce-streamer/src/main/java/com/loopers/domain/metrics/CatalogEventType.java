package com.loopers.domain.metrics;

import java.util.Arrays;
import java.util.Optional;

/**
 * catalog-events 가 싣는 이벤트 종류와 그것이 좋아요 수에 주는 영향(delta)을 한곳에 캡슐화한다.
 * "LIKED → +1" 같은 결정을 이 enum 이 소유하므로, 소비 측은 분기 없이 delta 만 받아 반영한다.
 */
public enum CatalogEventType {

    LIKED(1),
    UNLIKED(-1);

    private final long likeDelta;

    CatalogEventType(long likeDelta) {
        this.likeDelta = likeDelta;
    }

    public long likeDelta() {
        return likeDelta;
    }

    public static Optional<CatalogEventType> from(String raw) {
        return Arrays.stream(values())
            .filter(type -> type.name().equals(raw))
            .findFirst();
    }
}
