package com.loopers.application.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingScoreUpdater;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetricsModel;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CatalogEventFacadeTest {

    private CatalogEventFacade facade;

    @Mock private EventHandledRepository eventHandledRepository;
    @Mock private ProductMetricsRepository productMetricsRepository;
    @Mock private RankingScoreUpdater rankingScoreUpdater;

    private static final String EVENT_ID = "event-1";
    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @BeforeEach
    void setUp() {
        facade = new CatalogEventFacade(eventHandledRepository, productMetricsRepository, rankingScoreUpdater, new ObjectMapper());
    }

    private String toJson(String eventId, String eventType, Long userId, Long productId) {
        try {
            return new ObjectMapper().writeValueAsString(new CatalogEventPayload(eventId, eventType, userId, productId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DisplayName("handle()을 호출할 때,")
    @Nested
    class Handle {

        @DisplayName("신규 상품에 PRODUCT_LIKED 이벤트 수신 시 product_metrics가 생성되고 likeCount가 1이 된다.")
        @Test
        void createsMetricsAndIncrementsLikeCount_whenProductHasNoMetricsYet() {
            // arrange
            String payload = toJson(EVENT_ID, CatalogEventPayload.PRODUCT_LIKED, USER_ID, PRODUCT_ID);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());
            given(productMetricsRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            facade.handle(payload);

            // assert
            ArgumentCaptor<ProductMetricsModel> metricsCaptor = ArgumentCaptor.forClass(ProductMetricsModel.class);
            then(productMetricsRepository).should().save(metricsCaptor.capture());
            assertThat(metricsCaptor.getValue().getLikeCount()).isEqualTo(1);

            ArgumentCaptor<EventHandledModel> handledCaptor = ArgumentCaptor.forClass(EventHandledModel.class);
            then(eventHandledRepository).should().save(handledCaptor.capture());
            assertThat(handledCaptor.getValue().getEventId()).isEqualTo(EVENT_ID);
            then(rankingScoreUpdater).should().onProductLiked(PRODUCT_ID);
        }

        @DisplayName("기존 product_metrics가 있는 상태에서 PRODUCT_LIKED 수신 시 likeCount가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenMetricsAlreadyExists() {
            // arrange
            ProductMetricsModel existing = new ProductMetricsModel(PRODUCT_ID);
            existing.incrementLikeCount(); // 기존 likeCount = 1
            String payload = toJson(EVENT_ID, CatalogEventPayload.PRODUCT_LIKED, USER_ID, PRODUCT_ID);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));

            // act
            facade.handle(payload);

            // assert
            assertThat(existing.getLikeCount()).isEqualTo(2);
            then(productMetricsRepository).should(never()).save(any());
        }

        @DisplayName("PRODUCT_UNLIKED 수신 시 likeCount가 1 감소하고 랭킹 감점이 반영된다.")
        @Test
        void decrementsLikeCount_whenUnliked() {
            // arrange
            ProductMetricsModel existing = new ProductMetricsModel(PRODUCT_ID);
            existing.incrementLikeCount();
            existing.incrementLikeCount(); // likeCount = 2
            String payload = toJson(EVENT_ID, CatalogEventPayload.PRODUCT_UNLIKED, USER_ID, PRODUCT_ID);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));

            // act
            facade.handle(payload);

            // assert
            assertThat(existing.getLikeCount()).isEqualTo(1);
            then(rankingScoreUpdater).should().onProductUnliked(PRODUCT_ID);
        }

        @DisplayName("PRODUCT_VIEWED 수신 시 viewCount가 1 증가한다 (userId 없이도 처리된다).")
        @Test
        void incrementsViewCount_whenViewed() {
            // arrange
            ProductMetricsModel existing = new ProductMetricsModel(PRODUCT_ID);
            String payload = toJson(EVENT_ID, CatalogEventPayload.PRODUCT_VIEWED, null, PRODUCT_ID);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));

            // act
            facade.handle(payload);

            // assert
            assertThat(existing.getViewCount()).isEqualTo(1);
            then(rankingScoreUpdater).should().onProductViewed(PRODUCT_ID);
        }

        @DisplayName("이미 처리된 eventId면 집계·랭킹 반영 없이 멱등하게 무시한다.")
        @Test
        void skipsProcessing_whenEventAlreadyHandled() {
            // arrange
            String payload = toJson(EVENT_ID, CatalogEventPayload.PRODUCT_LIKED, USER_ID, PRODUCT_ID);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(true);

            // act
            facade.handle(payload);

            // assert
            then(productMetricsRepository).should(never()).findByProductId(any());
            then(eventHandledRepository).should(never()).save(any());
            then(rankingScoreUpdater).shouldHaveNoInteractions();
        }

        @DisplayName("지원하지 않는 eventType이면 IllegalArgumentException이 발생한다.")
        @Test
        void throwsIllegalArgumentException_whenEventTypeIsUnsupported() {
            // arrange
            ProductMetricsModel existing = new ProductMetricsModel(PRODUCT_ID);
            String payload = toJson(EVENT_ID, "UNKNOWN_EVENT", USER_ID, PRODUCT_ID);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));

            // act & assert
            assertThrows(IllegalArgumentException.class, () -> facade.handle(payload));
            then(eventHandledRepository).should(never()).save(any());
        }

        @DisplayName("파싱할 수 없는 페이로드면 IllegalArgumentException이 발생한다.")
        @Test
        void throwsIllegalArgumentException_whenPayloadIsMalformed() {
            // act & assert
            assertThrows(IllegalArgumentException.class, () -> facade.handle("not-a-json"));
            then(eventHandledRepository).should(never()).existsByEventId(any());
        }
    }
}
