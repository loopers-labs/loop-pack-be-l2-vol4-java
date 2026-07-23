package com.loopers.batch.job.productranking;

import java.math.BigDecimal;
import org.springframework.batch.item.ItemProcessor;

public class ProductRankingScoreProcessor
    implements ItemProcessor<ProductMetricItem, ProductRankingScore> {

  private static final BigDecimal VIEW_WEIGHT = new BigDecimal("0.1");
  private static final BigDecimal LIKE_WEIGHT = new BigDecimal("0.2");
  private static final BigDecimal ORDER_WEIGHT = new BigDecimal("1.0");

  @Override
  public ProductRankingScore process(ProductMetricItem item) {
    BigDecimal score =
        BigDecimal.valueOf(item.viewCount())
            .multiply(VIEW_WEIGHT)
            .add(BigDecimal.valueOf(item.likeCount()).multiply(LIKE_WEIGHT))
            .add(BigDecimal.valueOf(item.orderCount()).multiply(ORDER_WEIGHT));

    return new ProductRankingScore(item.productId(), score);
  }
}
