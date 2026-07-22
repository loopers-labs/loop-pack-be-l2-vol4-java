package com.loopers.domain.ranking;

import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import java.util.Objects;

public final class RankingScorePolicy {

  public static final double PRODUCT_VIEWED_SCORE = 0.1D;
  public static final double PRODUCT_LIKED_SCORE = 0.2D;
  public static final double PRODUCT_ORDERED_SCORE = 1.0D;

  private RankingScorePolicy() {}

  public static double score(ProductActivityEventType eventType) {
    Objects.requireNonNull(eventType, "eventType은 필수입니다.");
    return switch (eventType) {
      case PRODUCT_VIEWED -> PRODUCT_VIEWED_SCORE;
      case PRODUCT_LIKED -> PRODUCT_LIKED_SCORE;
      case PRODUCT_ORDERED -> PRODUCT_ORDERED_SCORE;
    };
  }
}
