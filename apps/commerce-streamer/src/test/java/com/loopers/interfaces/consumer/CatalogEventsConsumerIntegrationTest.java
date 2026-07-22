package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.DailyProductMetricsJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CatalogEventsConsumerIntegrationTest {

    private static final Acknowledgment NO_OP_ACK = () -> { };

    private final CatalogEventsConsumer catalogEventsConsumer;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final DailyProductMetricsJpaRepository dailyProductMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    CatalogEventsConsumerIntegrationTest(
        CatalogEventsConsumer catalogEventsConsumer,
        ProductMetricsJpaRepository productMetricsJpaRepository,
        DailyProductMetricsJpaRepository dailyProductMetricsJpaRepository,
        EventHandledJpaRepository eventHandledJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.catalogEventsConsumer = catalogEventsConsumer;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.dailyProductMetricsJpaRepository = dailyProductMetricsJpaRepository;
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("LIKED 이벤트를 받으면 product_metrics.like_count 가 1 증가하고 event_handled 에 1건 기록된다.")
    @Test
    void incrementsLikeCount_whenLikedConsumed() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadLikeCount(productId)).isEqualTo(1L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        );
    }

    @DisplayName("UNLIKED 이벤트를 받으면 like_count 가 1 감소한다.")
    @Test
    void decrementsLikeCount_whenUnlikedConsumed() {
        // given
        long productId = 1L;
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(record("evt-2", "LIKED", productId)), NO_OP_ACK);

        // when
        catalogEventsConsumer.consume(List.of(record("evt-3", "UNLIKED", productId)), NO_OP_ACK);

        // then
        assertThat(loadLikeCount(productId)).isEqualTo(1L);
    }

    @DisplayName("같은 event_id 를 두 번 받아도 like_count 는 한 번만 반영된다 — 멱등(At Least Once 흡수).")
    @Test
    void appliesOnce_whenSameEventConsumedTwice() {
        // given
        long productId = 1L;

        // when : 같은 이벤트가 재전송되어 두 번 소비된다
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadLikeCount(productId)).isEqualTo(1L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        );
    }

    @DisplayName("한 배치 안의 서로 다른 이벤트들이 모두 반영된다.")
    @Test
    void appliesAllEventsInBatch() {
        // given
        long productId = 1L;
        List<ConsumerRecord<String, byte[]>> batch = List.of(
            record("evt-1", "LIKED", productId),
            record("evt-2", "LIKED", productId),
            record("evt-3", "LIKED", productId)
        );

        // when
        catalogEventsConsumer.consume(batch, NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadLikeCount(productId)).isEqualTo(3L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(3L)
        );
    }

    @DisplayName("STOCK_CHANGED 를 처음 받으면 product_metrics 에 재고 스냅샷(stock_quantity/stock_version)이 생성된다.")
    @Test
    void createsStockSnapshot_whenFirstStockEventConsumed() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(stockRecord("evt-1", productId, 50, 5)), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadStockQuantity(productId)).isEqualTo(50L),
            () -> assertThat(loadStockVersion(productId)).isEqualTo(5L)
        );
    }

    @DisplayName("더 높은 version 의 재고 이벤트가 도착하면 최신 값으로 덮어쓴다.")
    @Test
    void overwritesStock_whenNewerVersionArrives() {
        // given
        long productId = 1L;
        catalogEventsConsumer.consume(List.of(stockRecord("evt-1", productId, 50, 5)), NO_OP_ACK);

        // when
        catalogEventsConsumer.consume(List.of(stockRecord("evt-2", productId, 70, 7)), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadStockQuantity(productId)).isEqualTo(70L),
            () -> assertThat(loadStockVersion(productId)).isEqualTo(7L)
        );
    }

    @DisplayName("순서가 뒤바뀌어 더 낮은 version 의 재고 이벤트가 뒤늦게 도착해도 최신 상태를 되돌리지 않는다(최신성 가드).")
    @Test
    void keepsLatestStock_whenOlderVersionArrivesLater() {
        // given : version 5(수량 50)가 먼저 반영된 상태
        long productId = 1L;
        catalogEventsConsumer.consume(List.of(stockRecord("evt-1", productId, 50, 5)), NO_OP_ACK);

        // when : 재전송/순서역전으로 더 오래된 version 3(수량 30)이 뒤늦게 도착
        catalogEventsConsumer.consume(List.of(stockRecord("evt-2", productId, 30, 3)), NO_OP_ACK);

        // then : 오래된 이벤트는 무시되어 최신(version 5, 수량 50)이 유지된다
        assertAll(
            () -> assertThat(loadStockQuantity(productId)).isEqualTo(50L),
            () -> assertThat(loadStockVersion(productId)).isEqualTo(5L)
        );
    }

    @DisplayName("VIEWED 이벤트를 받으면 product_metrics.view_count 가 1 증가한다.")
    @Test
    void incrementsViewCount_whenViewedConsumed() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(viewRecord("evt-1", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(viewRecord("evt-2", productId)), NO_OP_ACK);

        // then
        assertThat(loadViewCount(productId)).isEqualTo(2L);
    }

    @DisplayName("LIKED 이벤트를 받으면 daily_product_metrics 의 오늘 날짜 행에 like_count 가 1 증가한다.")
    @Test
    void incrementsDailyLikeCount_whenLikedConsumed() {
        // given
        long productId = 1L;
        LocalDate today = LocalDate.now();

        // when
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        assertThat(loadDailyLikeCount(productId, today)).isEqualTo(1L);
    }

    @DisplayName("VIEWED 이벤트를 받으면 daily_product_metrics 의 오늘 날짜 행에 view_count 가 누적된다.")
    @Test
    void incrementsDailyViewCount_whenViewedConsumed() {
        // given
        long productId = 1L;
        LocalDate today = LocalDate.now();

        // when
        catalogEventsConsumer.consume(List.of(viewRecord("evt-1", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(viewRecord("evt-2", productId)), NO_OP_ACK);

        // then
        assertThat(loadDailyViewCount(productId, today)).isEqualTo(2L);
    }

    @DisplayName("같은 event_id 를 두 번 받아도 daily like_count 는 한 번만 반영된다 — 멱등.")
    @Test
    void appliesDailyOnce_whenSameEventConsumedTwice() {
        // given
        long productId = 1L;
        LocalDate today = LocalDate.now();

        // when
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        assertThat(loadDailyLikeCount(productId, today)).isEqualTo(1L);
    }

    private long loadDailyLikeCount(long productId, LocalDate metricDate) {
        return dailyProductMetricsJpaRepository.findByProductIdAndMetricDate(productId, metricDate)
            .orElseThrow().getLikeCount();
    }

    private long loadDailyViewCount(long productId, LocalDate metricDate) {
        return dailyProductMetricsJpaRepository.findByProductIdAndMetricDate(productId, metricDate)
            .orElseThrow().getViewCount();
    }

    private long loadLikeCount(long productId) {
        return productMetricsJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    private long loadViewCount(long productId) {
        return productMetricsJpaRepository.findById(productId).orElseThrow().getViewCount();
    }

    private long loadStockQuantity(long productId) {
        return productMetricsJpaRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    private long loadStockVersion(long productId) {
        return productMetricsJpaRepository.findById(productId).orElseThrow().getStockVersion();
    }

    private ConsumerRecord<String, byte[]> record(String eventId, String eventType, long productId) {
        String json = """
            {"eventId":"%s","eventType":"%s","aggregateId":%d,"data":{"productId":%d,"type":"%s"}}
            """.formatted(eventId, eventType, productId, productId, eventType);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }

    private ConsumerRecord<String, byte[]> viewRecord(String eventId, long productId) {
        String json = """
            {"eventId":"%s","eventType":"VIEWED","aggregateId":%d,"data":{}}
            """.formatted(eventId, productId);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }

    private ConsumerRecord<String, byte[]> stockRecord(String eventId, long productId, long quantity, long version) {
        String json = """
            {"eventId":"%s","eventType":"STOCK_CHANGED","aggregateId":%d,"data":{"quantity":%d,"version":%d}}
            """.formatted(eventId, productId, quantity, version);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }
}
