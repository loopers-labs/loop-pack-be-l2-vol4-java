package com.loopers.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingScoreUpdater;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetricsDailyModel;
import com.loopers.domain.productmetrics.ProductMetricsDailyRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OrderEventFacadeTest {

    private OrderEventFacade facade;

    @Mock private EventHandledRepository eventHandledRepository;
    @Mock private ProductMetricsRepository productMetricsRepository;
    @Mock private ProductMetricsDailyRepository productMetricsDailyRepository;
    @Mock private RankingScoreUpdater rankingScoreUpdater;

    private static final String EVENT_ID = "order-event-1";
    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PRODUCT_ID = 10L;

    @BeforeEach
    void setUp() {
        facade = new OrderEventFacade(eventHandledRepository, productMetricsRepository, productMetricsDailyRepository, rankingScoreUpdater, new ObjectMapper());
    }

    private String toJson(String eventId, String eventType, Long orderId, Long userId, List<OrderEventPayload.Item> items) {
        try {
            return new ObjectMapper().writeValueAsString(new OrderEventPayload(eventId, eventType, orderId, userId, items));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DisplayName("handle()을 호출할 때,")
    @Nested
    class Handle {

        @DisplayName("ORDER_PAID 수신 시 상품별 수량만큼 salesCount가 증가하고, 단가·수량 기반 랭킹 반영이 호출된다 (신규 상품 포함).")
        @Test
        void incrementsSalesCountPerItem_whenOrderPaid() {
            // arrange
            List<OrderEventPayload.Item> items = List.of(
                new OrderEventPayload.Item(PRODUCT_ID, 2, 5_000),
                new OrderEventPayload.Item(PRODUCT_ID + 1, 3, 10_000)
            );
            String payload = toJson(EVENT_ID, OrderEventPayload.ORDER_PAID, ORDER_ID, USER_ID, items);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());
            given(productMetricsRepository.findByProductId(PRODUCT_ID + 1)).willReturn(Optional.empty());
            given(productMetricsRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(productMetricsDailyRepository.findByProductIdAndMetricDate(any(), any())).willReturn(Optional.empty());
            given(productMetricsDailyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            facade.handle(payload);

            // assert
            ArgumentCaptor<ProductMetricsModel> captor = ArgumentCaptor.forClass(ProductMetricsModel.class);
            then(productMetricsRepository).should(times(2)).save(captor.capture());
            List<ProductMetricsModel> saved = captor.getAllValues();
            assertThat(saved).anySatisfy(m -> assertThat(m.getSalesCount()).isEqualTo(2));
            assertThat(saved).anySatisfy(m -> assertThat(m.getSalesCount()).isEqualTo(3));

            // 일간 롤업도 상품별로 오늘 날짜 행이 생성되고 판매 수량이 반영된다.
            ArgumentCaptor<ProductMetricsDailyModel> dailyCaptor = ArgumentCaptor.forClass(ProductMetricsDailyModel.class);
            then(productMetricsDailyRepository).should(times(2)).save(dailyCaptor.capture());
            List<ProductMetricsDailyModel> savedDaily = dailyCaptor.getAllValues();
            assertThat(savedDaily).allSatisfy(d -> assertThat(d.getMetricDate()).isEqualTo(LocalDate.now()));
            assertThat(savedDaily).anySatisfy(d -> assertThat(d.getSalesCount()).isEqualTo(2));
            assertThat(savedDaily).anySatisfy(d -> assertThat(d.getSalesCount()).isEqualTo(3));

            ArgumentCaptor<EventHandledModel> handledCaptor = ArgumentCaptor.forClass(EventHandledModel.class);
            then(eventHandledRepository).should().save(handledCaptor.capture());
            assertThat(handledCaptor.getValue().getEventId()).isEqualTo(EVENT_ID);

            then(rankingScoreUpdater).should().onOrderPaid(PRODUCT_ID, 5_000, 2);
            then(rankingScoreUpdater).should().onOrderPaid(PRODUCT_ID + 1, 10_000, 3);
        }

        @DisplayName("기존 product_metrics가 있는 상품은 salesCount가 기존 값에 수량만큼 더해진다.")
        @Test
        void addsToExistingSalesCount() {
            // arrange
            ProductMetricsModel existing = new ProductMetricsModel(PRODUCT_ID);
            existing.incrementSalesCount(5);
            ProductMetricsDailyModel existingDaily = new ProductMetricsDailyModel(PRODUCT_ID, LocalDate.now());
            existingDaily.incrementSalesCount(3); // 오늘 이미 3건 판매
            List<OrderEventPayload.Item> items = List.of(new OrderEventPayload.Item(PRODUCT_ID, 2, 5_000));
            String payload = toJson(EVENT_ID, OrderEventPayload.ORDER_PAID, ORDER_ID, USER_ID, items);
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);
            given(productMetricsRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));
            given(productMetricsDailyRepository.findByProductIdAndMetricDate(any(), any())).willReturn(Optional.of(existingDaily));

            // act
            facade.handle(payload);

            // assert
            assertThat(existing.getSalesCount()).isEqualTo(7);
            assertThat(existingDaily.getSalesCount()).isEqualTo(5); // 오늘분 3 + 이번 2
            then(productMetricsRepository).should(never()).save(any());
            then(productMetricsDailyRepository).should(never()).save(any());
        }

        @DisplayName("이미 처리된 eventId면 집계·랭킹 반영 없이 멱등하게 무시한다.")
        @Test
        void skipsProcessing_whenEventAlreadyHandled() {
            // arrange
            List<OrderEventPayload.Item> items = List.of(new OrderEventPayload.Item(PRODUCT_ID, 1, 5_000));
            String payload = toJson(EVENT_ID, OrderEventPayload.ORDER_PAID, ORDER_ID, USER_ID, items);
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
            String payload = toJson(EVENT_ID, "UNKNOWN_EVENT", ORDER_ID, USER_ID, List.of());
            given(eventHandledRepository.existsByEventId(EVENT_ID)).willReturn(false);

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
