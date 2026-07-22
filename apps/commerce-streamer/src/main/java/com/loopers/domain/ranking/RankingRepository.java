package com.loopers.domain.ranking;

import java.util.UUID;

public interface RankingRepository {

  boolean incrementIfUnprocessed(RankingKeys keys, UUID eventId, Long productId, double score);
}
