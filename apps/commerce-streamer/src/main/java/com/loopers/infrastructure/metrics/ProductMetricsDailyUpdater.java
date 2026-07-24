package com.loopers.infrastructure.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * product_metrics_daily 에 <b>일자별 델타</b>를 UPSERT 한다(week10 — 주간/월간 랭킹의 원천).
 *
 * <p>{@link ProductMetricsUpdater}(누적 read model)와 달리 여기는 "그날 발생한 양"만 쌓는다.
 * 두 테이블은 {@code MetricsAggregator} 의 <b>같은 트랜잭션</b>에서 갱신되므로 서로 어긋나지 않는다.
 *
 * <p><b>행 선생성이 없다</b>: {@code product_metrics} 는 commerce-api 가 상품 생성 시 행을 만들어 두어
 * streamer 가 UPDATE 만 하면 됐지만, 일자별 행은 그날 첫 이벤트가 와야 존재를 알 수 있다. 그래서
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 로 없으면 만들고 있으면 더한다.
 *
 * <p><b>GREATEST(0, ...) 가드를 걸지 않는 이유</b>: {@code product_metrics.like_count} 는 "현재 좋아요 수"라
 * 음수가 의미를 갖지 않아 0 으로 눌렀지만, 여기 {@code like_delta} 는 "그날의 증감"이라 <b>음수가 정상</b>이다
 * (취소가 더 많았던 날). 구간 합산 시 그대로 상쇄돼야 한다.
 */
@Component
@RequiredArgsConstructor
public class ProductMetricsDailyUpdater {

    /**
     * MySQL {@code VALUES(col)} 는 8.0.20 부터 deprecated 지만 여전히 동작하며, 별칭 문법
     * ({@code ... AS new ON DUPLICATE KEY UPDATE c = c + new.c})은 8.0.19+ 를 요구한다.
     * 구버전 호환을 위해 {@code VALUES()} 를 유지한다.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO product_metrics_daily
                (metric_date, product_id, view_count, like_delta, sales_count, sales_amount, order_score,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            ON DUPLICATE KEY UPDATE
                view_count   = view_count   + VALUES(view_count),
                like_delta   = like_delta   + VALUES(like_delta),
                sales_count  = sales_count  + VALUES(sales_count),
                sales_amount = sales_amount + VALUES(sales_amount),
                order_score  = order_score  + VALUES(order_score),
                updated_at   = now()
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 배치 내에서 합산된 {@code (일자, 상품) → 델타} 를 한 번에 UPSERT 한다.
     * 모든 지표가 0 인 항목은 건너뛴다(빈 행을 만들 이유가 없다).
     */
    public void applyDailyDeltas(Map<DailyKey, DailyDelta> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = new ArrayList<>(deltas.size());
        for (Map.Entry<DailyKey, DailyDelta> entry : deltas.entrySet()) {
            DailyKey key = entry.getKey();
            DailyDelta d = entry.getValue();
            if (d.isEmpty()) {
                continue;
            }
            batchArgs.add(new Object[]{
                    key.metricDate(), key.productId(),
                    d.viewCount(), d.likeDelta(), d.salesCount(), d.salesAmount(), d.orderScore()
            });
        }
        if (batchArgs.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
    }

    /** 일자별 집계 키. 일자는 이벤트 <b>발생시각</b>(KST) 기준이다. */
    public record DailyKey(LocalDate metricDate, Long productId) {
    }

    /**
     * 하루치 지표 델타. 배치 내 합산용이라 불변 record + {@link #plus} 로 누적한다.
     *
     * <p>{@code orderScore} 는 주문 <b>건별</b>로 계산된 스코어의 합이다(자세한 이유는
     * {@code ProductMetricsDailyEntity#orderScore} javadoc 참고).
     */
    public record DailyDelta(long viewCount, long likeDelta, long salesCount, long salesAmount, double orderScore) {

        public static DailyDelta ofView(long count) {
            return new DailyDelta(count, 0, 0, 0, 0.0);
        }

        public static DailyDelta ofLike(long delta) {
            return new DailyDelta(0, delta, 0, 0, 0.0);
        }

        public static DailyDelta ofOrder(long quantity, long amount, double orderScore) {
            return new DailyDelta(0, 0, quantity, amount, orderScore);
        }

        public DailyDelta plus(DailyDelta other) {
            return new DailyDelta(
                    viewCount + other.viewCount,
                    likeDelta + other.likeDelta,
                    salesCount + other.salesCount,
                    salesAmount + other.salesAmount,
                    orderScore + other.orderScore);
        }

        /**
         * 모든 지표가 0 인가. 좋아요 +1 과 -1 이 같은 날 상쇄된 경우가 대표적이며,
         * 이때는 UPSERT 를 건너뛰어도 합산 결과가 같다.
         */
        public boolean isEmpty() {
            return viewCount == 0 && likeDelta == 0 && salesCount == 0 && salesAmount == 0 && orderScore == 0.0;
        }
    }
}
