package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyPolicy;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScorePolicy;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingEventProcessor {

  private static final int SUPPORTED_EVENT_VERSION = 1;

  private final RankingRepository rankingRepository;

  public boolean process(ProductActivityEventMessage event) {
    validate(event);
    RankingKeys keys = RankingKeyPolicy.dailyKeys(event.occurredAt());
    double score = RankingScorePolicy.score(event.eventType());
    return rankingRepository.incrementIfUnprocessed(
        keys, event.eventId(), event.productId(), score);
  }

  private void validate(ProductActivityEventMessage event) {
    if (event == null) {
      throw new IllegalArgumentException("이벤트는 필수입니다.");
    }
    if (event.version() != SUPPORTED_EVENT_VERSION) {
      throw new IllegalArgumentException("지원하지 않는 이벤트 버전입니다: " + event.version());
    }
    if (event.eventId() == null) {
      throw new IllegalArgumentException("eventId는 필수입니다.");
    }
    if (event.eventType() == null) {
      throw new IllegalArgumentException("eventType은 필수입니다.");
    }
    if (event.occurredAt() == null) {
      throw new IllegalArgumentException("occurredAt은 필수입니다.");
    }
    if (event.productId() == null || event.productId() <= 0) {
      throw new IllegalArgumentException("productId는 양수여야 합니다.");
    }
  }
}
