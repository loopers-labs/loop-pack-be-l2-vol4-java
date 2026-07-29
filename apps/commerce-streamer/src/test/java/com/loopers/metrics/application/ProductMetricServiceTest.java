package com.loopers.metrics.application;

import com.loopers.metrics.domain.ProductMetric;
import com.loopers.metrics.domain.ProductMetricId;
import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.metrics.interfaces.OrderPaidMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매량 = 결제완료 이벤트가 담아 온 수량을 그대로 증분(self-contained). SSOT 재조회 없음.
 * 재전달되면 중복 누적되는 best-effort 지표이고, 정합성 교정은 배치 reconcile 의 몫이다.
 * 집계 단위는 (stat_date, product_id) 이며 날짜는 컨슈머가 정해 넘긴다.
 */
@SpringBootTest
class ProductMetricServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 22);

    private final ProductMetricService productMetricService;
    private final ProductMetricJpaRepository productMetricJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricServiceTest(ProductMetricService productMetricService,
                             ProductMetricJpaRepository productMetricJpaRepository,
                             DatabaseCleanUp databaseCleanUp) {
        this.productMetricService = productMetricService;
        this.productMetricJpaRepository = productMetricJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderPaidMessage paid(String eventId, OrderPaidMessage.Line... lines) {
        return new OrderPaidMessage(eventId, 1L, List.of(lines), ZonedDateTime.now());
    }

    private long salesCount(long productId, LocalDate statDate) {
        return productMetricJpaRepository.findById(new ProductMetricId(productId, statDate))
                .map(ProductMetric::getSalesCount)
                .orElse(0L);
    }

    @Test
    @DisplayName("결제완료 이벤트가 담은 수량만큼 판매량을 증분한다")
    void givenPaidEvent_whenApply_thenSalesIncreasedByEventQuantity() {
        productMetricService.apply(paid("evt-1",
                new OrderPaidMessage.Line(100L, 3),
                new OrderPaidMessage.Line(100L, 2)), DATE);

        assertThat(salesCount(100L, DATE)).isEqualTo(5);
    }

    @Test
    @DisplayName("한 이벤트에 담긴 여러 상품은 상품별로 각각 집계된다")
    void givenMultipleProducts_whenApply_thenEachAggregated() {
        productMetricService.apply(paid("evt-2",
                new OrderPaidMessage.Line(100L, 1),
                new OrderPaidMessage.Line(200L, 4)), DATE);

        assertThat(salesCount(100L, DATE)).isEqualTo(1);
        assertThat(salesCount(200L, DATE)).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 이벤트가 재전달되면 delta 가 중복 누적된다(best-effort, 정합성은 배치 reconcile 이 보정)")
    void givenRedeliveredEvent_whenAppliedTwice_thenDoubleCounted() {
        OrderPaidMessage event = paid("evt-3", new OrderPaidMessage.Line(100L, 3));

        productMetricService.apply(event, DATE);
        productMetricService.apply(event, DATE);

        assertThat(salesCount(100L, DATE)).isEqualTo(6);
    }

    @Test
    @DisplayName("같은 상품이라도 결제일이 다르면 날짜별로 나뉘어 쌓인다")
    void givenPaidOnDifferentDates_whenApply_thenAggregatedPerDate() {
        LocalDate nextDay = DATE.plusDays(1);

        productMetricService.apply(paid("evt-4", new OrderPaidMessage.Line(100L, 3)), DATE);
        productMetricService.apply(paid("evt-5", new OrderPaidMessage.Line(100L, 2)), nextDay);

        assertThat(salesCount(100L, DATE)).isEqualTo(3);
        assertThat(salesCount(100L, nextDay)).isEqualTo(2);
    }
}
