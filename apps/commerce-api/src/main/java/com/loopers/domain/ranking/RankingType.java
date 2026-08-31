package com.loopers.domain.ranking;

public enum RankingType {
    DAILY("ranking:all:"),
    HOURLY("ranking:hourly:"),
    WEEKLY,
    MONTHLY;

    private final String keyPrefix;

    RankingType(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    RankingType() {
        this.keyPrefix = null;
    }

    public String toKey(String date) {
        if (keyPrefix == null) {
            throw new UnsupportedOperationException("RankingType." + name() + " does not support Redis key");
        }
        return keyPrefix + date;
    }
}
