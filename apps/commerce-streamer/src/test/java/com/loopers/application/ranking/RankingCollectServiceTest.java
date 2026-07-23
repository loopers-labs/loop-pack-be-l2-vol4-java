package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.domain.ranking.RankingSlot;
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
import java.util.List;
import java.util.Map;
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
        rankingCollectService.collectCatalogBatch(List.of(catalog(CatalogEventType.PRODUCT_VIEWED, 1L)));

        verify(rankingRepository).incrementAll(Map.of(new RankingSlot(RankingSignal.VIEW, BUCKET, 1L), 1.0));
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("PRODUCT_LIKED → like 보드 +1")
    void liked() {
        rankingCollectService.collectCatalogBatch(List.of(catalog(CatalogEventType.PRODUCT_LIKED, 2L)));

        verify(rankingRepository).incrementAll(Map.of(new RankingSlot(RankingSignal.LIKE, BUCKET, 2L), 1.0));
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("PRODUCT_UNLIKED → like 보드 -1 (취소 미반영은 사이클 펌프 경로)")
    void unliked() {
        rankingCollectService.collectCatalogBatch(List.of(catalog(CatalogEventType.PRODUCT_UNLIKED, 3L)));

        verify(rankingRepository).incrementAll(Map.of(new RankingSlot(RankingSignal.LIKE, BUCKET, 3L), -1.0));
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("PRODUCT_SOLD → order_count +1 과 order_qty +수량, 두 보드에 각각")
    void sold() {
        rankingCollectService.collectOrderBatch(List.of(order(4L, 5)));

        verify(rankingRepository).incrementAll(Map.of(
                new RankingSlot(RankingSignal.ORDER_COUNT, BUCKET, 4L), 1.0,
                new RankingSlot(RankingSignal.ORDER_QTY, BUCKET, 4L), 5.0));
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("버킷은 처리 시각이 아닌 occurredAt(KST 번역) 기준이다 — UTC 15일 23시 = KST 16일")
    void bucketFollowsOccurredAt() {
        ZonedDateTime lateNightUtc = ZonedDateTime.of(2026, 7, 15, 23, 0, 0, 0, ZoneOffset.UTC);
        rankingCollectService.collectCatalogBatch(List.of(new CatalogEventMessage(
                UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, 5L, null, lateNightUtc)));

        verify(rankingRepository).incrementAll(
                Map.of(new RankingSlot(RankingSignal.VIEW, LocalDate.of(2026, 7, 16), 5L), 1.0));
    }

    @Test
    @DisplayName("배치 수집은 같은 (신호,날짜,상품) 슬롯의 델타를 리스너 안에서 합산해 incrementAll 1회로 쓴다")
    void batchAggregatesSameSlot() {
        rankingCollectService.collectCatalogBatch(List.of(
                catalog(CatalogEventType.PRODUCT_VIEWED, 1L),
                catalog(CatalogEventType.PRODUCT_VIEWED, 1L),
                catalog(CatalogEventType.PRODUCT_VIEWED, 1L),
                catalog(CatalogEventType.PRODUCT_LIKED, 1L),
                catalog(CatalogEventType.PRODUCT_UNLIKED, 1L)));

        verify(rankingRepository).incrementAll(Map.of(
                new RankingSlot(RankingSignal.VIEW, BUCKET, 1L), 3.0,
                new RankingSlot(RankingSignal.LIKE, BUCKET, 1L), 0.0)); // +1 -1 합산
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("배치 안의 매핑 불가 요소는 skip 되고 나머지는 반영된다 — 한 요소가 배치를 인수하지 않는다")
    void batchSkipsPoisonElement() {
        CatalogEventMessage poison = new CatalogEventMessage(
                UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, 9L, null, null); // occurredAt 없음 → NPE

        rankingCollectService.collectCatalogBatch(List.of(
                catalog(CatalogEventType.PRODUCT_VIEWED, 1L),
                poison,
                catalog(CatalogEventType.PRODUCT_VIEWED, 2L)));

        verify(rankingRepository).incrementAll(Map.of(
                new RankingSlot(RankingSignal.VIEW, BUCKET, 1L), 1.0,
                new RankingSlot(RankingSignal.VIEW, BUCKET, 2L), 1.0));
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("주문 배치는 건수·수량 슬롯으로 나뉘어 합산된다")
    void orderBatchSplitsCountAndQty() {
        rankingCollectService.collectOrderBatch(List.of(
                order(4L, 5),
                order(4L, 2)));

        verify(rankingRepository).incrementAll(Map.of(
                new RankingSlot(RankingSignal.ORDER_COUNT, BUCKET, 4L), 2.0,
                new RankingSlot(RankingSignal.ORDER_QTY, BUCKET, 4L), 7.0));
        verifyNoMoreInteractions(rankingRepository);
    }

    private CatalogEventMessage catalog(CatalogEventType type, long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), type, productId, 1L, OCCURRED_AT);
    }

    private OrderEventMessage order(long productId, int quantity) {
        return new OrderEventMessage(
                UUID.randomUUID().toString(), OrderEventType.PRODUCT_SOLD, productId, quantity, 100L, 1L, OCCURRED_AT);
    }
}
