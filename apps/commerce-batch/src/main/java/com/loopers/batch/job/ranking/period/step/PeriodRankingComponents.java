package com.loopers.batch.job.ranking.period.step;

import com.loopers.batch.job.ranking.period.PeriodMetricsSum;
import com.loopers.batch.job.ranking.period.PeriodRange;
import com.loopers.batch.job.ranking.period.PeriodScore;
import com.loopers.batch.job.ranking.period.RankingPeriodType;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;

import javax.sql.DataSource;
import java.sql.Date;

/**
 * 주간/월간 잡이 공유하는 청크 컴포넌트(Reader/Writer) 생성기.
 *
 * <p>두 잡은 대상 기간과 MV 테이블만 다르고 읽기·쓰기 구조가 같아 여기서 만들어 쓴다.
 * 스텝 스코프 바인딩({@code @Value("#{jobParameters[...]}")})은 각 JobConfig 의 {@code @Bean} 이 담당한다.
 */
public final class PeriodRankingComponents {

    /**
     * 구간 집계 쿼리. {@code product_metrics_daily} 의 PK 가 {@code (metric_date, product_id)} 라
     * 선두 컬럼이 날짜이므로 이 {@code BETWEEN} 은 PK 레인지 스캔으로 커버된다.
     */
    private static final String READ_SQL = """
            SELECT product_id,
                   SUM(view_count)  AS view_sum,
                   SUM(like_delta)  AS like_sum,
                   SUM(order_score) AS order_score_sum
              FROM product_metrics_daily
             WHERE metric_date BETWEEN ? AND ?
             GROUP BY product_id
            """;

    private static final String STAGING_INSERT_SQL = """
            INSERT INTO product_rank_staging (period_type, period_start, product_id, score)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE score = VALUES(score)
            """;

    private PeriodRankingComponents() {
    }

    /**
     * 구간 집계 결과를 스트리밍으로 읽는다.
     *
     * <p><b>왜 커서인가</b>: {@code JdbcPagingItemReader} 는 페이지마다 쿼리를 다시 던지는데, 이 쿼리는
     * {@code GROUP BY} 집계라 매 페이지에서 전체 그룹화를 반복하게 된다(사실상 O(n²)). 커서는 서버가 만든
     * 결과집합을 한 번만 훑으므로 대량 구간에서도 비용이 선형이다. 배치라 커넥션을 오래 잡는 건 허용된다.
     *
     * <p>{@code setVerifyCursorPosition(false)} — 커서 위치 검증은 행마다 확인 비용이 들고, 우리는
     * ResultSet 을 한 방향으로만 읽으므로 불필요하다.
     */
    public static JdbcCursorItemReader<PeriodMetricsSum> reader(DataSource dataSource, PeriodRange range) {
        return new JdbcCursorItemReaderBuilder<PeriodMetricsSum>()
                .name("periodMetricsSumReader")
                .dataSource(dataSource)
                .sql(READ_SQL)
                .queryArguments(Date.valueOf(range.start()), Date.valueOf(range.end()))
                .rowMapper((rs, rowNum) -> new PeriodMetricsSum(
                        rs.getLong("product_id"),
                        rs.getLong("view_sum"),
                        rs.getLong("like_sum"),
                        rs.getDouble("order_score_sum")))
                .verifyCursorPosition(false)
                .build();
    }

    /**
     * 스코어를 staging 에 적재한다. 순위는 아직 없다(전역 정렬이 필요하므로 Step2 가 확정).
     *
     * <p>{@code ON DUPLICATE KEY UPDATE} 로 같은 기간을 재실행해도 행이 겹치지 않는다 — Step1 앞에서
     * 해당 {@code (period_type, period_start)} 를 지우지만, 청크 중간 실패 후 재시작하는 경우까지 덮어 안전하다.
     */
    public static JdbcBatchItemWriter<PeriodScore> stagingWriter(
            DataSource dataSource, RankingPeriodType type, PeriodRange range) {
        return new JdbcBatchItemWriterBuilder<PeriodScore>()
                .dataSource(dataSource)
                .sql(STAGING_INSERT_SQL)
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setString(1, type.code());
                    ps.setDate(2, Date.valueOf(range.start()));
                    ps.setLong(3, item.productId());
                    ps.setDouble(4, item.score());
                })
                .assertUpdates(false) // UPSERT 는 갱신 행수가 0/1/2 로 달라져 검증이 의미 없다
                .build();
    }
}
