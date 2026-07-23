package com.loopers.tddstudy.infrastructure.ranking;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class ProductRankMv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_key")
    private String periodKey;

    @Column(name = "rank_no")
    private int rankNo;

    @Column(name = "product_id")
    private Long productId;

    private double score;
    private long likeCount;
    private long salesCount;
    private long viewCount;
    private LocalDateTime createdAt;

    protected ProductRankMv() {}

    protected ProductRankMv(String periodKey, int rankNo, Long productId, double score,
                            long likeCount, long salesCount, long viewCount) {
        this.periodKey = periodKey;
        this.rankNo = rankNo;
        this.productId = productId;
        this.score = score;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.viewCount = viewCount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getPeriodKey() { return periodKey; }
    public int getRankNo() { return rankNo; }
    public Long getProductId() { return productId; }
    public double getScore() { return score; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public long getViewCount() { return viewCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
