package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.interfaces.consumer.CatalogEventMessage;
import com.loopers.interfaces.consumer.CatalogEventType;
import com.loopers.interfaces.consumer.OrderEventMessage;
import com.loopers.interfaces.consumer.OrderEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("RankingCollectService 단위 테스트 — 이벤트 → (신호, 델타) 매핑")
@ExtendWith(MockitoExtension.class)
class RankingCollectServiceTest {

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private RankingCollectService rankingCollectService;

    private static final ZonedDateTime OCCURRED_AT =
            ZonedDateTime.of(2026, 7, 16, 10, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final LocalDate BUCKET = LocalDate.of(2026, 7, 16);

    @Test
    @DisplayName("PRODUCT_VIEWED → view 보드 +1")
    void viewed() {
        rankingCollectService.collect(catalog(CatalogEventType.PRODUCT_VIEWED, 1L));

        verify(rankingRepository).increment(RankingSignal.VIEW, BUCKET, 1L, 1);
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("PRODUCT_LIKED → like 보드 +1")
    void liked() {
        rankingCollectService.collect(catalog(CatalogEventType.PRODUCT_LIKED, 2L));

        verify(rankingRepository).increment(RankingSignal.LIKE, BUCKET, 2L, 1);
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("PRODUCT_UNLIKED → like 보드 -1 (취소 미반영은 사이클 펌프 경로)")
    void unliked() {
        rankingCollectService.collect(catalog(CatalogEventType.PRODUCT_UNLIKED, 3L));

        verify(rankingRepository).increment(RankingSignal.LIKE, BUCKET, 3L, -1);
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("PRODUCT_SOLD → order_count +1 과 order_qty +수량, 두 보드에 각각")
    void sold() {
        rankingCollectService.collect(new OrderEventMessage(
                UUID.randomUUID().toString(), OrderEventType.PRODUCT_SOLD, 4L, 5, 100L, 1L, OCCURRED_AT));

        verify(rankingRepository).increment(RankingSignal.ORDER_COUNT, BUCKET, 4L, 1);
        verify(rankingRepository).increment(RankingSignal.ORDER_QTY, BUCKET, 4L, 5);
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("버킷은 처리 시각이 아닌 occurredAt(KST 번역) 기준이다 — UTC 15일 23시 = KST 16일")
    void bucketFollowsOccurredAt() {
        ZonedDateTime lateNightUtc = ZonedDateTime.of(2026, 7, 15, 23, 0, 0, 0, ZoneOffset.UTC);
        rankingCollectService.collect(new CatalogEventMessage(
                UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, 5L, null, lateNightUtc));

        verify(rankingRepository).increment(RankingSignal.VIEW, LocalDate.of(2026, 7, 16), 5L, 1);
    }

    private CatalogEventMessage catalog(CatalogEventType type, long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), type, productId, 1L, OCCURRED_AT);
    }
}
