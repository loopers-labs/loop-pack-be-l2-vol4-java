package com.loopers.batch.job.productranking;

public record ProductRankingBatchSettings(int chunkSize) {

  public ProductRankingBatchSettings {
    if (chunkSize < 1) {
      throw new IllegalArgumentException("product-ranking.batch.chunk-size는 1 이상이어야 합니다.");
    }
  }
}
