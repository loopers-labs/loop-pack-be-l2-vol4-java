package com.loopers.ranking.perf;

import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.DatasetDefinition;
import com.loopers.ranking.perf.ProductMetricPerfDataGenerator.ProductMetricSeedRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Profile("perf-seed & !test & !local & !dev & !qa & !prd")
@Component
public class ProductMetricPerfDataSeeder implements CommandLineRunner {

    private static final int INSERT_BATCH_SIZE = 10_000;
    private static final int PROGRESS_LOG_INTERVAL = 100_000;
    private static final String CREATE_MANIFEST_TABLE_SQL = """
        create table if not exists perf_dataset_manifest (
            dataset_name varchar(50) not null primary key,
            dataset_version varchar(50) not null,
            generator_version int not null,
            random_seed bigint not null,
            metric_start_date date not null,
            metric_end_date date not null,
            product_count bigint not null,
            active_product_count bigint not null,
            metric_count bigint not null,
            completed_at datetime(6) null
        )
        """;
    private static final String INSERT_METRIC_SQL = """
        insert into product_metrics(
            metric_date,
            product_id,
            view_count,
            like_delta,
            order_quantity,
            order_amount,
            updated_at
        )
        values (?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ProductMetricPerfDataGenerator generator;
    private final DatasetDefinition dataset;
    private final int batchSize;
    private final String expectedDatabase;
    private final Clock clock;

    @Autowired
    public ProductMetricPerfDataSeeder(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        @Value("${commerce.ranking.perf-seed.expected-database}")
        String expectedDatabase
    ) {
        this(
            jdbcTemplate,
            transactionManager,
            ProductMetricPerfDataGenerator.defaultGenerator(),
            INSERT_BATCH_SIZE,
            expectedDatabase,
            Clock.systemUTC()
        );
    }

    ProductMetricPerfDataSeeder(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        ProductMetricPerfDataGenerator generator,
        int batchSize,
        String expectedDatabase,
        Clock clock
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 양수여야 합니다.");
        }
        if (expectedDatabase == null || expectedDatabase.isBlank()) {
            throw new IllegalArgumentException(
                "성능 Dataset을 생성할 전용 DB 이름이 필요합니다."
            );
        }
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(
            Objects.requireNonNull(transactionManager)
        );
        this.generator = Objects.requireNonNull(generator);
        this.dataset = generator.dataset();
        this.batchSize = batchSize;
        this.expectedDatabase = expectedDatabase;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void run(String... args) {
        verifyTargetDatabase();
        jdbcTemplate.execute(CREATE_MANIFEST_TABLE_SQL);

        Optional<DatasetManifest> existingManifest = findManifest();
        if (existingManifest.isPresent()) {
            verifyReusableDataset(existingManifest.get());
            log.info(
                "성능 Dataset을 재사용합니다. datasetName={}, metricCount={}",
                dataset.datasetName(),
                dataset.metricCount()
            );
            return;
        }

        long existingMetricCount = metricCount();
        if (existingMetricCount != 0) {
            throw new IllegalStateException(
                "Manifest 없이 product_metrics가 존재합니다. 전용 perf DB를 확인해주세요."
            );
        }

        insertIncompleteManifest();
        long insertedMetricCount = seedMetrics();
        if (insertedMetricCount != dataset.metricCount()) {
            throw new IllegalStateException(
                "생성한 Metric 건수가 Dataset 정의와 일치하지 않습니다. expected=%d, actual=%d"
                    .formatted(dataset.metricCount(), insertedMetricCount)
            );
        }

        verifyMetricSummary(readMetricSummary());
        completeManifest();
        log.info(
            "성능 Dataset 생성을 완료했습니다. datasetName={}, productCount={}, "
                + "activeProductCount={}, metricCount={}",
            dataset.datasetName(),
            dataset.productCount(),
            dataset.activeProductCount(),
            dataset.metricCount()
        );
    }

    private void verifyTargetDatabase() {
        String actualDatabase = jdbcTemplate.queryForObject(
            "select database()",
            String.class
        );
        if (!expectedDatabase.equals(actualDatabase)) {
            throw new IllegalStateException(
                "성능 Dataset 대상 DB가 일치하지 않습니다. expected=%s, actual=%s"
                    .formatted(expectedDatabase, actualDatabase)
            );
        }
    }

    private void verifyReusableDataset(DatasetManifest manifest) {
        if (manifest.completedAt() == null) {
            throw new IllegalStateException(
                "미완성 성능 Dataset이 남아 있습니다. 전용 perf DB를 초기화해주세요."
            );
        }
        if (!manifest.matches(dataset)) {
            throw new IllegalStateException(
                "완료 Manifest 설정이 현재 Dataset 정의와 일치하지 않습니다."
            );
        }
        verifyMetricSummary(readMetricSummary());
    }

    private Optional<DatasetManifest> findManifest() {
        List<DatasetManifest> manifests = jdbcTemplate.query(
            """
                select
                    dataset_name,
                    dataset_version,
                    generator_version,
                    random_seed,
                    metric_start_date,
                    metric_end_date,
                    product_count,
                    active_product_count,
                    metric_count,
                    completed_at
                from perf_dataset_manifest
                """,
            (resultSet, rowNumber) -> new DatasetManifest(
                resultSet.getString("dataset_name"),
                resultSet.getString("dataset_version"),
                resultSet.getInt("generator_version"),
                resultSet.getLong("random_seed"),
                resultSet.getObject("metric_start_date", LocalDate.class),
                resultSet.getObject("metric_end_date", LocalDate.class),
                resultSet.getLong("product_count"),
                resultSet.getLong("active_product_count"),
                resultSet.getLong("metric_count"),
                resultSet.getObject("completed_at", LocalDateTime.class)
            )
        );
        if (manifests.size() > 1) {
            throw new IllegalStateException(
                "perf_dataset_manifest에는 단 하나의 Dataset만 존재해야 합니다."
            );
        }
        if (manifests.isEmpty()) {
            return Optional.empty();
        }

        DatasetManifest manifest = manifests.getFirst();
        if (!manifest.datasetName().equals(dataset.datasetName())) {
            throw new IllegalStateException(
                "Manifest의 Dataset 이름이 현재 정의와 일치하지 않습니다. expected=%s, actual=%s"
                    .formatted(dataset.datasetName(), manifest.datasetName())
            );
        }
        return Optional.of(manifest);
    }

    private void insertIncompleteManifest() {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
            """
                insert into perf_dataset_manifest(
                    dataset_name,
                    dataset_version,
                    generator_version,
                    random_seed,
                    metric_start_date,
                    metric_end_date,
                    product_count,
                    active_product_count,
                    metric_count,
                    completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, null)
                """,
            dataset.datasetName(),
            dataset.datasetVersion(),
            dataset.generatorVersion(),
            dataset.randomSeed(),
            dataset.metricStartDate(),
            dataset.metricEndDate(),
            dataset.productCount(),
            dataset.activeProductCount(),
            dataset.metricCount()
        ));
    }

    private long seedMetrics() {
        List<Object[]> batch = new ArrayList<>(batchSize);
        long[] insertedCount = {0};

        long generatedCount = generator.generate(metric -> {
            batch.add(toParameters(metric));
            if (batch.size() == batchSize) {
                writeBatch(batch);
                insertedCount[0] += batch.size();
                batch.clear();
                logProgress(insertedCount[0]);
            }
        });

        if (!batch.isEmpty()) {
            writeBatch(batch);
            insertedCount[0] += batch.size();
            batch.clear();
        }
        if (generatedCount != insertedCount[0]) {
            throw new IllegalStateException(
                "생성 건수와 저장 요청 건수가 일치하지 않습니다. generated=%d, inserted=%d"
                    .formatted(generatedCount, insertedCount[0])
            );
        }
        return insertedCount[0];
    }

    private Object[] toParameters(ProductMetricSeedRow metric) {
        return new Object[]{
            metric.metricDate(),
            metric.productId(),
            metric.viewCount(),
            metric.likeDelta(),
            metric.orderQuantity(),
            metric.orderAmount(),
            metric.updatedAt()
        };
    }

    private void writeBatch(List<Object[]> batch) {
        transactionTemplate.executeWithoutResult(status ->
            jdbcTemplate.batchUpdate(INSERT_METRIC_SQL, batch)
        );
    }

    private void logProgress(long insertedCount) {
        if (insertedCount % PROGRESS_LOG_INTERVAL == 0) {
            log.info(
                "성능 Metric 생성 중입니다. inserted={}, expected={}",
                insertedCount,
                dataset.metricCount()
            );
        }
    }

    private MetricSummary readMetricSummary() {
        return jdbcTemplate.queryForObject(
            """
                select
                    count(*) as metric_count,
                    count(distinct product_id) as active_product_count,
                    min(metric_date) as metric_start_date,
                    max(metric_date) as metric_end_date
                from product_metrics
                """,
            (resultSet, rowNumber) -> new MetricSummary(
                resultSet.getLong("metric_count"),
                resultSet.getLong("active_product_count"),
                resultSet.getObject("metric_start_date", LocalDate.class),
                resultSet.getObject("metric_end_date", LocalDate.class)
            )
        );
    }

    private long metricCount() {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from product_metrics",
            Long.class
        );
        return count == null ? 0 : count;
    }

    private void verifyMetricSummary(MetricSummary summary) {
        if (summary.metricCount() != dataset.metricCount()
            || summary.activeProductCount() != dataset.activeProductCount()
            || !Objects.equals(summary.metricStartDate(), dataset.metricStartDate())
            || !Objects.equals(summary.metricEndDate(), dataset.metricEndDate())) {
            throw new IllegalStateException(
                "실제 Metric 요약이 Manifest와 일치하지 않습니다. expected=%s, actual=%s"
                    .formatted(expectedSummary(), summary)
            );
        }
    }

    private MetricSummary expectedSummary() {
        return new MetricSummary(
            dataset.metricCount(),
            dataset.activeProductCount(),
            dataset.metricStartDate(),
            dataset.metricEndDate()
        );
    }

    private void completeManifest() {
        LocalDateTime completedAt = LocalDateTime.ofInstant(
            clock.instant(),
            java.time.ZoneOffset.UTC
        );
        transactionTemplate.executeWithoutResult(status -> {
            int updated = jdbcTemplate.update(
                """
                    update perf_dataset_manifest
                    set completed_at = ?
                    where dataset_name = ?
                      and completed_at is null
                    """,
                completedAt,
                dataset.datasetName()
            );
            if (updated != 1) {
                throw new IllegalStateException(
                    "성능 Dataset 완료 상태를 기록하지 못했습니다."
                );
            }
        });
    }

    private record DatasetManifest(
        String datasetName,
        String datasetVersion,
        int generatorVersion,
        long randomSeed,
        LocalDate metricStartDate,
        LocalDate metricEndDate,
        long productCount,
        long activeProductCount,
        long metricCount,
        LocalDateTime completedAt
    ) {

        boolean matches(DatasetDefinition definition) {
            return datasetName.equals(definition.datasetName())
                && datasetVersion.equals(definition.datasetVersion())
                && generatorVersion == definition.generatorVersion()
                && randomSeed == definition.randomSeed()
                && metricStartDate.equals(definition.metricStartDate())
                && metricEndDate.equals(definition.metricEndDate())
                && productCount == definition.productCount()
                && activeProductCount == definition.activeProductCount()
                && metricCount == definition.metricCount();
        }
    }

    private record MetricSummary(
        long metricCount,
        long activeProductCount,
        LocalDate metricStartDate,
        LocalDate metricEndDate
    ) {
    }
}
