package com.loopers.batch.job.catalog.ranking;

import java.util.Locale;

public enum ProductRankingPeriod {
    WEEKLY("mv_product_rank_weekly"),
    MONTHLY("mv_product_rank_monthly");

    private final String tableName;

    ProductRankingPeriod(String tableName) {
        this.tableName = tableName;
    }

    public static ProductRankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("period JobParameter는 필수입니다.");
        }

        try {
            return ProductRankingPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("period JobParameter는 weekly 또는 monthly여야 합니다.", e);
        }
    }

    public String tableName() {
        return tableName;
    }
}
