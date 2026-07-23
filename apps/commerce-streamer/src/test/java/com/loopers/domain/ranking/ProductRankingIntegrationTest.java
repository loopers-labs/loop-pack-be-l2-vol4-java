package com.loopers.domain.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class ProductRankingIntegrationTest {

    private static final String KEY = "ranking:all:" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    @Autowired
    private ProductMetricsService productMetricsService;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("조회 이벤트는 오늘 랭킹 키에 0.1 점을 누적한다.")
    @Test
    void view_addsViewWeight() {
        // arrange & act
        productMetricsService.applyView("v1", 100L);

        // assert
        assertThat(score(100L)).isCloseTo(0.1, within(1e-9));
    }

    @DisplayName("좋아요 등록은 +0.2, 취소는 -0.2 로 누적된다.")
    @Test
    void like_addsAndSubtractsLikeWeight() {
        // arrange
        productMetricsService.applyLike("l1", 100L, 1L);
        assertThat(score(100L)).isCloseTo(0.2, within(1e-9));

        // act
        productMetricsService.applyLike("l2", 100L, -1L);

        // assert
        assertThat(score(100L)).isCloseTo(0.0, within(1e-9));
    }

    @DisplayName("주문 이벤트는 0.7 × log10(1 + subtotal) 을 누적한다 (subtotal 9999 → 2.8).")
    @Test
    void order_addsOrderWeightTimesLogSubtotal() {
        // arrange & act
        productMetricsService.applyOrderPaid("o1", List.of(new ProductMetricsService.OrderItem(100L, 1L, 9999L)));

        // assert
        assertThat(score(100L)).isCloseTo(2.8, within(1e-9));
    }

    @DisplayName("주문 1건이 좋아요 3건(0.6)보다 상위에 랭크된다 (주문 신호 우선).")
    @Test
    void order_ranksAboveThreeLikes() {
        // arrange
        productMetricsService.applyOrderPaid("o1", List.of(new ProductMetricsService.OrderItem(100L, 1L, 10000L)));
        productMetricsService.applyLike("l1", 200L, 1L);
        productMetricsService.applyLike("l2", 200L, 1L);
        productMetricsService.applyLike("l3", 200L, 1L);

        // act
        List<String> ranked = List.copyOf(redisTemplate.opsForZSet().reverseRange(KEY, 0, -1));

        // assert
        assertThat(ranked).containsExactly("100", "200");
    }

    @DisplayName("같은 eventId 로 두 번 소비돼도 랭킹 점수는 한 번만 반영된다 (멱등).")
    @Test
    void idempotent_onDuplicateEventId() {
        // arrange & act
        productMetricsService.applyView("dup", 100L);
        productMetricsService.applyView("dup", 100L);

        // assert
        assertThat(score(100L)).isCloseTo(0.1, within(1e-9));
    }

    @DisplayName("적재 시 오늘 키에 TTL(2일 이내)이 설정된다.")
    @Test
    void ttl_isSetWithinTwoDays() {
        // arrange & act
        productMetricsService.applyView("v1", 100L);

        // assert
        Long ttlSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(172800L);
    }

    private Double score(Long productId) {
        return redisTemplate.opsForZSet().score(KEY, String.valueOf(productId));
    }
}