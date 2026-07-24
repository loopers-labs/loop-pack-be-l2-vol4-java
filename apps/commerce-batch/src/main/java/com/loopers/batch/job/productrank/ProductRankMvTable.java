package com.loopers.batch.job.productrank;

// MV upsert/cleanup SQL에 삽입되는 테이블명을 이 enum 값으로 제한해, 임의 문자열이 SQL에 그대로
// 삽입되는 것을 막는다 (현재는 상수로만 쓰이지만 향후 호출부가 늘어나도 안전하도록 타입으로 고정).
public enum ProductRankMvTable {
    WEEKLY("mv_product_rank_weekly"),
    MONTHLY("mv_product_rank_monthly");

    private final String tableName;

    ProductRankMvTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
