package com.loopers.ranking.perf;

import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.DatasetDefinition;
import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.ProductGroup;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class ProductMetricPerfDataSeederIntegrationTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final DatasetDefinition DATASET = new DatasetDefinition(
        "product-ranking-test",
        "ranking-perf-test-v1",
        1,
        18L,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 4),
        List.of(
            new ProductGroup(2, 4, 1_000, 2_000),
            new ProductGroup(2, 2, 500, 1_000),
            new ProductGroup(1, 1, 100, 500),
            new ProductGroup(1, 0, 0, 0)
        )
    );

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseCleanUp databaseCleanUp;
    private final ApplicationContext applicationContext;

    @Autowired
    ProductMetricPerfDataSeederIntegrationTest(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        DatabaseCleanUp databaseCleanUp,
        ApplicationContext applicationContext
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.databaseCleanUp = databaseCleanUp;
        this.applicationContext = applicationContext;
    }

    @AfterEach
    void tearDown() {
        dropSecondBatchFailureConstraint();
        jdbcTemplate.execute("drop table if exists perf_dataset_manifest");
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("고정 Dataset을 작은 JDBC Batch로 생성하고 검증 완료 시각을 기록한다.")
    @Test
    void seedsDeterministicDatasetAndCompletesManifest() throws Exception {
        // arrange
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET);

        // act
        seeder.run();

        // assert
        assertThat(metricCount()).isEqualTo(13);
        assertThat(activeProductCount()).isEqualTo(5);
        assertThat(metricDateRange()).containsExactly(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 4)
        );
        assertThat(negativeLikeMetricCount()).isPositive();
        assertThat(metricUpdatedAtRange()).containsExactly(
            LocalDateTime.of(2026, 7, 5, 0, 0),
            LocalDateTime.of(2026, 7, 5, 0, 0)
        );

        assertThat(jdbcTemplate.queryForMap(
            "select * from perf_dataset_manifest where dataset_name = ?",
            DATASET.datasetName()
        ))
            .containsEntry("dataset_version", DATASET.datasetVersion())
            .containsEntry("generator_version", DATASET.generatorVersion())
            .containsEntry("random_seed", DATASET.randomSeed())
            .containsEntry("product_count", 6L)
            .containsEntry("active_product_count", 5L)
            .containsEntry("metric_count", 13L)
            .containsKey("completed_at");
        assertThat(jdbcTemplate.queryForObject(
            """
                select completed_at
                from perf_dataset_manifest
                where dataset_name = ?
                """,
            LocalDateTime.class,
            DATASET.datasetName()
        )).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    @DisplayName("일반 test 프로필에서는 대용량 Seeder Bean을 등록하지 않는다.")
    @Test
    void seederBeanIsNotRegisteredForTestProfile() {
        assertThat(applicationContext.getBeansOfType(ProductMetricPerfDataSeeder.class))
            .isEmpty();
    }

    @DisplayName("명시한 전용 DB와 실제 접속 DB가 다르면 아무것도 만들지 않고 실패한다.")
    @Test
    void unexpectedDatabaseIsRejectedBeforeSeeding() {
        // arrange
        ProductMetricPerfDataSeeder seeder = new ProductMetricPerfDataSeeder(
            jdbcTemplate,
            transactionManager,
            new ProductMetricPerfDataGenerator(DATASET),
            3,
            "unexpected_database",
            Clock.fixed(GENERATED_AT, ZoneOffset.UTC)
        );

        // act & assert
        assertThatThrownBy(seeder::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("대상 DB가 일치하지 않습니다");
        assertThat(metricCount()).isZero();
    }

    @DisplayName("같은 완료 Dataset이 이미 유효하면 기존 Metric을 바꾸지 않고 종료한다.")
    @Test
    void completedMatchingDatasetIsNoOp() throws Exception {
        // arrange
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET);
        seeder.run();
        jdbcTemplate.update(
            """
                update product_metrics
                set view_count = 999999
                order by metric_date, product_id
                limit 1
                """
        );

        // act
        seeder.run();

        // assert
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from product_metrics where view_count = 999999",
            Long.class
        )).isOne();
        assertThat(metricCount()).isEqualTo(13);
    }

    @DisplayName("Manifest 없이 Metric이 존재하면 기존 데이터를 삭제하거나 덮어쓰지 않고 실패한다.")
    @Test
    void metricsWithoutManifestAreRejected() {
        // arrange
        insertExistingMetric();
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET);

        // act & assert
        assertThatThrownBy(seeder::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Manifest");
        assertThat(metricCount()).isOne();
    }

    @DisplayName("미완성 Manifest가 남아 있으면 자동 재생성하지 않고 실패한다.")
    @Test
    void incompleteManifestIsRejected() throws Exception {
        // arrange
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET);
        seeder.run();
        jdbcTemplate.update(
            """
                update perf_dataset_manifest
                set completed_at = null
                where dataset_name = ?
                """,
            DATASET.datasetName()
        );
        long existingMetricCount = metricCount();

        // act & assert
        assertThatThrownBy(seeder::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("미완성");
        assertThat(metricCount()).isEqualTo(existingMetricCount);
    }

    @DisplayName("다음 JDBC Batch가 실패해도 이전 Batch는 남고 Manifest는 미완성으로 유지된다.")
    @Test
    void failedLaterBatchKeepsEarlierCommitAndIncompleteManifest() {
        // arrange
        jdbcTemplate.execute("""
            alter table product_metrics
            add constraint ck_reject_second_perf_metric check (product_id <> 2)
            """);
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET, 1);

        // act & assert
        assertThatThrownBy(seeder::run)
            .hasMessageContaining("ck_reject_second_perf_metric");
        assertThat(metricCount()).isOne();
        assertThat(jdbcTemplate.queryForObject(
            """
                select completed_at is null
                from perf_dataset_manifest
                where dataset_name = ?
                """,
            Boolean.class,
            DATASET.datasetName()
        )).isTrue();
    }

    @DisplayName("완료 Manifest와 실제 Metric 건수가 다르면 기존 데이터를 덮어쓰지 않고 실패한다.")
    @Test
    void completedDatasetWithMismatchedMetricsIsRejected() throws Exception {
        // arrange
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET);
        seeder.run();
        jdbcTemplate.update(
            """
                delete from product_metrics
                order by metric_date desc, product_id desc
                limit 1
                """
        );
        long existingMetricCount = metricCount();

        // act & assert
        assertThatThrownBy(seeder::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("일치하지 않습니다");
        assertThat(metricCount()).isEqualTo(existingMetricCount);
    }

    @DisplayName("다른 이름의 Manifest가 있으면 새 Dataset을 나란히 생성하지 않고 실패한다.")
    @Test
    void differentDatasetManifestIsRejected() throws Exception {
        // arrange
        ProductMetricPerfDataSeeder seeder = createSeeder(DATASET);
        seeder.run();
        jdbcTemplate.update("delete from product_metrics");
        jdbcTemplate.update(
            """
                update perf_dataset_manifest
                set dataset_name = 'different-product-ranking'
                where dataset_name = ?
                """,
            DATASET.datasetName()
        );

        // act & assert
        assertThatThrownBy(seeder::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Dataset 이름");
        assertThat(metricCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from perf_dataset_manifest",
            Long.class
        )).isOne();
    }

    private ProductMetricPerfDataSeeder createSeeder(DatasetDefinition dataset) {
        return createSeeder(dataset, 3);
    }

    private ProductMetricPerfDataSeeder createSeeder(
        DatasetDefinition dataset,
        int batchSize
    ) {
        return new ProductMetricPerfDataSeeder(
            jdbcTemplate,
            transactionManager,
            new ProductMetricPerfDataGenerator(dataset),
            batchSize,
            currentDatabase(),
            Clock.fixed(GENERATED_AT, ZoneOffset.UTC)
        );
    }

    private String currentDatabase() {
        return jdbcTemplate.queryForObject("select database()", String.class);
    }

    private long metricCount() {
        return jdbcTemplate.queryForObject(
            "select count(*) from product_metrics",
            Long.class
        );
    }

    private long activeProductCount() {
        return jdbcTemplate.queryForObject(
            "select count(distinct product_id) from product_metrics",
            Long.class
        );
    }

    private List<LocalDate> metricDateRange() {
        return jdbcTemplate.queryForObject(
            "select min(metric_date), max(metric_date) from product_metrics",
            (resultSet, rowNumber) -> List.of(
                resultSet.getObject(1, LocalDate.class),
                resultSet.getObject(2, LocalDate.class)
            )
        );
    }

    private long negativeLikeMetricCount() {
        return jdbcTemplate.queryForObject(
            "select count(*) from product_metrics where like_delta < 0",
            Long.class
        );
    }

    private List<LocalDateTime> metricUpdatedAtRange() {
        return jdbcTemplate.queryForObject(
            "select min(updated_at), max(updated_at) from product_metrics",
            (resultSet, rowNumber) -> List.of(
                resultSet.getObject(1, LocalDateTime.class),
                resultSet.getObject(2, LocalDateTime.class)
            )
        );
    }

    private void insertExistingMetric() {
        jdbcTemplate.update(
            """
                insert into product_metrics(
                    metric_date,
                    product_id,
                    view_count,
                    like_delta,
                    order_quantity,
                    order_amount,
                    updated_at
                )
                values (?, ?, 1, 0, 0, 0, ?)
                """,
            LocalDate.of(2026, 7, 1),
            1L,
            GENERATED_AT
        );
    }

    private void dropSecondBatchFailureConstraint() {
        Long constraintCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = database()
                  and table_name = 'product_metrics'
                  and constraint_name = 'ck_reject_second_perf_metric'
                """,
            Long.class
        );
        if (constraintCount != null && constraintCount == 1) {
            jdbcTemplate.execute("""
                alter table product_metrics
                drop check ck_reject_second_perf_metric
                """);
        }
    }
}
