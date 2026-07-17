package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.interfaces.api.product.ProductV1Dto;
import java.util.List;

public class RankingV1Dto {

  public record RankingItemResponse(long rank, double score, ProductV1Dto.ProductResponse product) {
    public static RankingItemResponse from(RankingItemInfo info) {
      return new RankingItemResponse(
          info.rank(), info.score(), ProductV1Dto.ProductResponse.from(info.product()));
    }
  }

  public record RankingPageResponse(
      List<RankingItemResponse> items, int page, int size, long totalCount, int totalPages) {
    public static RankingPageResponse from(RankingPageInfo info) {
      return new RankingPageResponse(
          info.items().stream().map(RankingItemResponse::from).toList(),
          info.page(),
          info.size(),
          info.totalCount(),
          info.totalPages());
    }
  }
}
