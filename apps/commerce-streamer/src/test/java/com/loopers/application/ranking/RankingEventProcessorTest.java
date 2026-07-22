package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingEventProcessorTest {

  private final RankingRepository rankingRepository = mock(RankingRepository.class);
  private final RankingEventProcessor processor = new RankingEventProcessor(rankingRepository);

  @DisplayName("이벤트 유형의 고정 점수를 서울 날짜의 랭킹 키에 반영한다.")
  @Test
  void appliesWeightedScoreToDailyRanking() {
    UUID eventId = UUID.randomUUID();
    ProductActivityEventMessage event =
        new ProductActivityEventMessage(
            eventId,
            1,
            ProductActivityEventType.PRODUCT_ORDERED,
            Instant.parse("2026-07-15T15:00:00Z"),
            10L,
            50_000L,
            3);

    processor.process(event);

    verify(rankingRepository)
        .incrementIfUnprocessed(
            new RankingKeys(
                "ranking:all:20260716", "ranking:hour:2026071600", "ranking:processed:20260716"),
            eventId,
            10L,
            1.0D);
  }

  @DisplayName("지원하지 않는 버전은 랭킹에 반영하지 않는다.")
  @Test
  void rejectsUnsupportedVersion() {
    ProductActivityEventMessage event =
        new ProductActivityEventMessage(
            UUID.randomUUID(),
            2,
            ProductActivityEventType.PRODUCT_VIEWED,
            Instant.now(),
            1L,
            null,
            null);

    assertThatThrownBy(() -> processor.process(event))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("버전");
  }
}
