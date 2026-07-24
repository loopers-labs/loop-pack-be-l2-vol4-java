package com.loopers.metrics.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMetricEventHandlerTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");
    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 7, 2);
    private static final Instant HANDLED_AT = Instant.parse("2026-07-02T02:00:00Z");
    private static final EventHandlingMetadata METADATA = new EventHandlingMetadata("catalog-events", 0, 10L);

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @Mock
    private ProductMetricHourlyRepository productMetricHourlyRepository;

    @Mock
    private CatalogMetricsMetrics catalogMetricsMetrics;

    @Mock
    private Clock clock;

    @InjectMocks
    private ProductMetricEventHandler handler;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(HANDLED_AT);
    }

    @DisplayName("catalog 이벤트로 상품 지표를 집계할 때")
    @Nested
    class Handle {

        @DisplayName("이미 처리한 이벤트면 상품 지표를 다시 변경하지 않는다.")
        @Test
        void skipsMetrics_whenEventAlreadyHandled() {
            // arrange
            CatalogEventEnvelope event = likedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(false);

            // act
            handler.handle(event, METADATA);

            // assert
            verify(productMetricsRepository, never()).addAll(any(), any());
            verify(productMetricHourlyRepository, never()).addAll(any(), any());
        }

        @DisplayName("상품 조회 이벤트면 조회수를 1 증가시킨다.")
        @Test
        void increasesViewCount_whenProductViewed() {
            // arrange
            CatalogEventEnvelope event = viewedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(
                new ProductMetricDelta(METRIC_DATE, 101L, 1, 0, 0, 0)
            );
        }

        @DisplayName("신규 상품 이벤트면 발생 시간 Window의 Raw Metric을 저장한다.")
        @Test
        void addsHourlyRawMetric_whenEventIsNew() {
            // arrange
            CatalogEventEnvelope event = viewedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ArgumentCaptor<List<ProductMetricHourlyDelta>> captor = listCaptor();
            verify(productMetricHourlyRepository).addAll(captor.capture(), any());
            assertThat(captor.getValue()).containsExactly(new ProductMetricHourlyDelta(
                OCCURRED_AT.toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                101L,
                1,
                0,
                0,
                0
            ));
        }

        @DisplayName("좋아요 이벤트면 좋아요 수를 1 증가시킨다.")
        @Test
        void increasesLikeCount_whenProductLiked() {
            // arrange
            CatalogEventEnvelope event = likedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(
                new ProductMetricDelta(METRIC_DATE, 101L, 0, 1, 0, 0)
            );
        }

        @DisplayName("좋아요 취소 이벤트면 좋아요 수를 1 감소시킨다.")
        @Test
        void decreasesLikeCount_whenProductUnliked() {
            // arrange
            CatalogEventEnvelope event = unlikedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(
                new ProductMetricDelta(METRIC_DATE, 101L, 0, -1, 0, 0)
            );
        }

        @DisplayName("상품 주문 이벤트면 주문 수량과 주문 금액을 함께 증가시킨다.")
        @Test
        void increasesOrderQuantityAndAmount_whenProductOrdered() {
            // arrange
            CatalogEventEnvelope event = orderedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(
                new ProductMetricDelta(METRIC_DATE, 101L, 0, 0, 2, 25_000)
            );
        }
    }

    @DisplayName("catalog 이벤트 Batch로 상품 지표를 집계할 때")
    @Nested
    class HandleBatch {

        @DisplayName("이미 처리한 이벤트를 제외하고 신규 이벤트만 지표에 반영한다")
        @Test
        void appliesMetricsOnlyForNewEvents() {
            // arrange
            ProductMetricEventCommand first = command(viewedEvent("event-1"), 10L);
            ProductMetricEventCommand duplicated = command(viewedEvent("event-1"), 11L);
            when(eventHandledRepository.saveIfAbsent(any(), any(), any()))
                .thenReturn(true, false);

            // act
            handler.handleBatch(List.of(first, duplicated));

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(
                new ProductMetricDelta(METRIC_DATE, 101L, 1, 0, 0, 0)
            );
            ArgumentCaptor<List<ProductMetricHourlyDelta>> hourlyCaptor = listCaptor();
            verify(productMetricHourlyRepository).addAll(hourlyCaptor.capture(), any());
            assertThat(hourlyCaptor.getValue()).containsExactly(new ProductMetricHourlyDelta(
                OCCURRED_AT.toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                101L,
                1,
                0,
                0,
                0
            ));
        }

        @DisplayName("같은 상품과 시간 Window의 신규 이벤트를 하나의 지표 Delta로 합산한다")
        @Test
        void aggregatesNewEventsForSameProductAndWindow() {
            // arrange
            ProductMetricEventCommand viewed = command(viewedEvent("event-1"), 10L);
            ProductMetricEventCommand liked = command(likedEvent("event-2"), 11L);
            ProductMetricEventCommand ordered = command(orderedEvent("event-3"), 12L);
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handleBatch(List.of(viewed, liked, ordered));

            // assert
            ArgumentCaptor<List<ProductMetricDelta>> metricCaptor = listCaptor();
            ArgumentCaptor<List<ProductMetricHourlyDelta>> hourlyCaptor = listCaptor();
            verify(productMetricsRepository).addAll(metricCaptor.capture(), any());
            verify(productMetricHourlyRepository).addAll(hourlyCaptor.capture(), any());
            assertThat(metricCaptor.getValue()).containsExactly(
                new ProductMetricDelta(METRIC_DATE, 101L, 1, 1, 2, 25_000)
            );
            assertThat(hourlyCaptor.getValue()).containsExactly(new ProductMetricHourlyDelta(
                OCCURRED_AT.toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                101L,
                1,
                1,
                2,
                25_000
            ));
        }

        @DisplayName("상품 또는 시간 Window가 다르면 별도 지표 Delta로 분리한다")
        @Test
        void separatesEventsForDifferentProductOrWindow() {
            // arrange
            ProductMetricEventCommand firstProductAtTen = command(
                viewedEvent("event-1", 101L, OCCURRED_AT),
                10L
            );
            ProductMetricEventCommand secondProductAtTen = command(
                viewedEvent("event-2", 202L, OCCURRED_AT.plusMinutes(10)),
                11L
            );
            ProductMetricEventCommand firstProductAtEleven = command(
                viewedEvent("event-3", 101L, OCCURRED_AT.plusHours(1)),
                12L
            );
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handleBatch(List.of(firstProductAtTen, secondProductAtTen, firstProductAtEleven));

            // assert
            ArgumentCaptor<List<ProductMetricDelta>> metricCaptor = listCaptor();
            ArgumentCaptor<List<ProductMetricHourlyDelta>> hourlyCaptor = listCaptor();
            verify(productMetricsRepository).addAll(metricCaptor.capture(), any());
            verify(productMetricHourlyRepository).addAll(hourlyCaptor.capture(), any());
            assertThat(metricCaptor.getValue()).containsExactly(
                new ProductMetricDelta(METRIC_DATE, 101L, 2, 0, 0, 0),
                new ProductMetricDelta(METRIC_DATE, 202L, 1, 0, 0, 0)
            );
            assertThat(hourlyCaptor.getValue()).containsExactly(
                new ProductMetricHourlyDelta(
                    OCCURRED_AT.toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                    101L,
                    1,
                    0,
                    0,
                    0
                ),
                new ProductMetricHourlyDelta(
                    OCCURRED_AT.plusMinutes(10).toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                    202L,
                    1,
                    0,
                    0,
                    0
                ),
                new ProductMetricHourlyDelta(
                    OCCURRED_AT.plusHours(1).toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                    101L,
                    1,
                    0,
                    0,
                    0
                )
            );
            verify(catalogMetricsMetrics).recordAggregation(3, 3, 2, 3);
        }

        @DisplayName("같은 상품이어도 서울 날짜가 다르면 별도의 일간 지표로 분리한다.")
        @Test
        void separatesDailyMetricsForDifferentSeoulDates() {
            // arrange
            ProductMetricEventCommand firstDate = command(
                viewedEvent("event-1", 101L, OCCURRED_AT),
                10L
            );
            ProductMetricEventCommand nextDate = command(
                viewedEvent("event-2", 101L, OCCURRED_AT.plusDays(1)),
                11L
            );
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handleBatch(List.of(firstDate, nextDate));

            // assert
            ArgumentCaptor<List<ProductMetricDelta>> captor = listCaptor();
            verify(productMetricsRepository).addAll(captor.capture(), eq(HANDLED_AT));
            assertThat(captor.getValue()).containsExactly(
                new ProductMetricDelta(METRIC_DATE, 101L, 1, 0, 0, 0),
                new ProductMetricDelta(METRIC_DATE.plusDays(1), 101L, 1, 0, 0, 0)
            );
            verify(catalogMetricsMetrics).recordAggregation(2, 2, 2, 2);
        }
    }

    private ProductMetricDelta captureDelta() {
        ArgumentCaptor<List<ProductMetricDelta>> captor = listCaptor();
        verify(productMetricsRepository).addAll(captor.capture(), eq(HANDLED_AT));
        assertThat(captor.getValue()).hasSize(1);
        return captor.getValue().getFirst();
    }

    @SuppressWarnings("unchecked")
    private <T> ArgumentCaptor<List<T>> listCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private ProductMetricEventCommand command(CatalogEventEnvelope event, long offset) {
        return new ProductMetricEventCommand(
            event,
            new EventHandlingMetadata("catalog-events", 0, offset)
        );
    }

    private CatalogEventEnvelope viewedEvent(String eventId) {
        return viewedEvent(eventId, 101L, OCCURRED_AT);
    }

    private CatalogEventEnvelope viewedEvent(String eventId, long productId, ZonedDateTime occurredAt) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_VIEWED,
            "PRODUCT",
            productId,
            new CatalogEventPayload(productId, 1L, 10L, null),
            occurredAt
        );
    }

    private CatalogEventEnvelope likedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_LIKED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, 1),
            OCCURRED_AT
        );
    }

    private CatalogEventEnvelope unlikedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_UNLIKED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, -1),
            OCCURRED_AT
        );
    }

    private CatalogEventEnvelope orderedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_ORDERED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(
                101L,
                1L,
                null,
                null,
                500L,
                2,
                12_500L,
                25_000L
            ),
            OCCURRED_AT
        );
    }
}
