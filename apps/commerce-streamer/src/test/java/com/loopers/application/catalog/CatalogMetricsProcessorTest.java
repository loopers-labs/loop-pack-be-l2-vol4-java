package com.loopers.application.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.catalog.EventHandledEntity;
import com.loopers.infrastructure.catalog.EventHandledJpaRepository;
import com.loopers.infrastructure.catalog.ProductDailyMetricsJpaRepository;
import com.loopers.infrastructure.catalog.ProductMetricsJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CatalogMetricsProcessorTest {

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Mock
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Mock
    private ProductDailyMetricsJpaRepository productDailyMetricsJpaRepository;

    private CatalogMetricsProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CatalogMetricsProcessor(eventHandledJpaRepository, productMetricsJpaRepository,
            productDailyMetricsJpaRepository, new ObjectMapper());
    }

    @DisplayName("process()를 실행할 때,")
    @Nested
    class Process {

        @DisplayName("이미 처리된 eventId이면 metrics를 업데이트하지 않는다.")
        @Test
        void skips_whenEventAlreadyHandled() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-1")).willReturn(true);

            processor.process("OrderItemSoldEvent", "uuid-1", "catalog-events-v1",
                "{\"orderId\":1,\"productQtyMap\":{\"10\":2}}");

            then(productMetricsJpaRepository).should(never()).upsertOrderCount(anyLong(), anyInt());
            then(productDailyMetricsJpaRepository).should(never()).upsertOrderCount(anyLong(), anyInt());
            then(eventHandledJpaRepository).should(never()).save(any());
        }

        @DisplayName("OrderItemSoldEvent 처리 시 productQtyMap 기준으로 product_metrics와 product_daily_metrics의 order_count를 함께 upsert한다.")
        @Test
        void upsertsOrderCount_whenOrderItemSoldEvent() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-2")).willReturn(false);

            processor.process("OrderItemSoldEvent", "uuid-2", "catalog-events-v1",
                "{\"orderId\":1,\"productQtyMap\":{\"10\":2,\"20\":3}}");

            then(productMetricsJpaRepository).should().upsertOrderCount(10L, 2);
            then(productMetricsJpaRepository).should().upsertOrderCount(20L, 3);
            then(productDailyMetricsJpaRepository).should().upsertOrderCount(10L, 2);
            then(productDailyMetricsJpaRepository).should().upsertOrderCount(20L, 3);
        }

        @DisplayName("ProductLikedEvent 처리 시 product_metrics와 product_daily_metrics의 like_count를 함께 1 증가시킨다.")
        @Test
        void incrementsLikeCount_whenProductLikedEvent() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-3")).willReturn(false);

            processor.process("ProductLikedEvent", "uuid-3", "catalog-events-v1",
                "{\"productId\":10}");

            then(productMetricsJpaRepository).should().upsertLikeCountIncrement(10L);
            then(productDailyMetricsJpaRepository).should().upsertLikeCountIncrement(10L);
        }

        @DisplayName("ProductUnlikedEvent 처리 시 product_metrics와 product_daily_metrics의 like_count를 함께 1 감소시킨다.")
        @Test
        void decrementsLikeCount_whenProductUnlikedEvent() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-4")).willReturn(false);

            processor.process("ProductUnlikedEvent", "uuid-4", "catalog-events-v1",
                "{\"productId\":10}");

            then(productMetricsJpaRepository).should().upsertLikeCountDecrement(10L);
            then(productDailyMetricsJpaRepository).should().upsertLikeCountDecrement(10L);
        }

        @DisplayName("ProductViewedEvent 처리 시 product_metrics와 product_daily_metrics의 view_count를 함께 1 증가시킨다.")
        @Test
        void incrementsViewCount_whenProductViewedEvent() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-7")).willReturn(false);

            processor.process("ProductViewedEvent", "uuid-7", "catalog-events-v1",
                "{\"productId\":10}");

            then(productMetricsJpaRepository).should().upsertViewCountIncrement(10L);
            then(productDailyMetricsJpaRepository).should().upsertViewCountIncrement(10L);
        }

        @DisplayName("처리 완료 후 eventHandled를 저장한다.")
        @Test
        void savesEventHandled_afterProcessing() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-5")).willReturn(false);

            processor.process("ProductLikedEvent", "uuid-5", "catalog-events-v1",
                "{\"productId\":10}");

            ArgumentCaptor<EventHandledEntity> captor = ArgumentCaptor.forClass(EventHandledEntity.class);
            then(eventHandledJpaRepository).should().save(captor.capture());
            assertThat(captor.getValue().getEventId()).isEqualTo("uuid-5");
            assertThat(captor.getValue().getTopic()).isEqualTo("catalog-events-v1");
        }

        @DisplayName("알 수 없는 이벤트 타입이면 예외 없이 eventHandled만 저장한다.")
        @Test
        void savesEventHandledOnly_whenUnknownEventType() throws Exception {
            given(eventHandledJpaRepository.existsByEventId("uuid-6")).willReturn(false);

            processor.process("UnknownEvent", "uuid-6", "catalog-events-v1", "{}");

            then(productMetricsJpaRepository).shouldHaveNoInteractions();
            then(productDailyMetricsJpaRepository).shouldHaveNoInteractions();
            then(eventHandledJpaRepository).should().save(any());
        }
    }
}
