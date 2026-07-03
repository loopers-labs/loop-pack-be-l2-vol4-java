package com.loopers.metrics.application;

import com.loopers.metrics.domain.ProductMetric;
import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.metrics.interfaces.OrderPaidMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매량 = order_items(SSOT) 재계산 덮어쓰기. 이벤트는 "이 상품 재계산" 트리거일 뿐이라 멱등.
 */
@SpringBootTest
class ProductMetricServiceTest {

    private final ProductMetricService productMetricService;
    private final ProductMetricJpaRepository productMetricJpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricServiceTest(ProductMetricService productMetricService,
                             ProductMetricJpaRepository productMetricJpaRepository,
                             JdbcTemplate jdbcTemplate,
                             DatabaseCleanUp databaseCleanUp) {
        this.productMetricService = productMetricService;
        this.productMetricJpaRepository = productMetricJpaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void seedOrderItem(long orderId, long productId, int quantity) {
        // 판매량은 PAID 주문만 센다 → 주문을 PAID 로 시딩.
        jdbcTemplate.update("""
                INSERT IGNORE INTO orders (id, status, created_at, updated_at)
                VALUES (?, 'PAID', NOW(6), NOW(6))
                """, orderId);
        jdbcTemplate.update("""
                INSERT INTO order_items (order_id, product_id, quantity, created_at, updated_at)
                VALUES (?, ?, ?, NOW(6), NOW(6))
                """, orderId, productId, quantity);
    }

    private OrderPaidMessage trigger(long productId) {
        return new OrderPaidMessage("evt-" + productId, 1L,
                List.of(new OrderPaidMessage.Line(productId, 1)), ZonedDateTime.now());
    }

    private long salesCount(long productId) {
        return productMetricJpaRepository.findById(productId).map(ProductMetric::getSalesCount).orElse(0L);
    }

    @Test
    @DisplayName("주문 이벤트를 트리거로 order_items 합계를 판매량으로 재계산한다")
    void givenOrderItems_whenApply_thenSalesRecomputed() {
        seedOrderItem(1L, 100L, 3);
        seedOrderItem(2L, 100L, 2);

        productMetricService.apply(trigger(100L));

        assertThat(salesCount(100L)).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 트리거를 두 번 처리해도 재계산이라 판매량은 동일하다(멱등)")
    void givenSameTrigger_whenAppliedTwice_thenSame() {
        seedOrderItem(1L, 100L, 3);

        productMetricService.apply(trigger(100L));
        productMetricService.apply(trigger(100L));

        assertThat(salesCount(100L)).isEqualTo(3);
    }

    @Test
    @DisplayName("결제 완료(PAID) 주문만 판매량에 반영되고 미결제 주문은 제외된다")
    void givenMixedOrders_whenApply_thenOnlyPaidCounted() {
        seedOrderItem(1L, 100L, 3); // PAID
        jdbcTemplate.update("INSERT IGNORE INTO orders (id, status, created_at, updated_at) VALUES (2, 'PENDING_PAYMENT', NOW(6), NOW(6))");
        jdbcTemplate.update("INSERT INTO order_items (order_id, product_id, quantity, created_at, updated_at) VALUES (2, 100, 5, NOW(6), NOW(6))");

        productMetricService.apply(trigger(100L));

        assertThat(salesCount(100L)).isEqualTo(3);
    }
}
