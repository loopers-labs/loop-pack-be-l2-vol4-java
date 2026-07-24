package com.loopers.benchmark.ranking.period;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class PeriodRankingBenchmarkReport {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PeriodRankingBenchmarkReport() {
    }

    static ReportFiles write(
        PeriodRankingBenchmarkConfig config,
        List<PeriodRankingStrategyResult> strategies,
        List<PeriodRankingChunkResult> chunks,
        List<PeriodRankingContentionResult> contention,
        Environment environment,
        Instant generatedAt
    ) throws IOException {
        if (strategies.isEmpty() || chunks.isEmpty() || contention.isEmpty()) {
            throw new IllegalArgumentException("all period ranking benchmark result groups are required");
        }
        Files.createDirectories(config.outputDir());
        String suffix = generatedAt.atZone(REPORT_ZONE).format(FILE_TIME) + "-" + safe(config.label());
        Path strategyCsv = config.outputDir().resolve("period-ranking-strategy-" + suffix + ".csv");
        Path chunkCsv = config.outputDir().resolve("period-ranking-chunk-" + suffix + ".csv");
        Path contentionCsv = config.outputDir().resolve("period-ranking-contention-" + suffix + ".csv");
        Path markdown = config.outputDir().resolve("period-ranking-" + suffix + ".md");
        Files.writeString(strategyCsv, strategyCsv(config, strategies), StandardCharsets.UTF_8);
        Files.writeString(chunkCsv, chunkCsv(config, chunks), StandardCharsets.UTF_8);
        Files.writeString(contentionCsv, contentionCsv(config, contention), StandardCharsets.UTF_8);
        Files.writeString(
            markdown,
            markdown(config, strategies, chunks, contention, environment, generatedAt),
            StandardCharsets.UTF_8
        );
        return new ReportFiles(strategyCsv, chunkCsv, contentionCsv, markdown);
    }

    static String strategyCsv(PeriodRankingBenchmarkConfig config, List<PeriodRankingStrategyResult> results) {
        StringBuilder csv = new StringBuilder(
            "label,run,strategy,cardinality,source_rows,period_days,active_hours,page_size,elapsed_ms,"
                + "products_per_sec,source_group_query_count,staging_page_count,staging_rows,score_digest\n"
        );
        for (PeriodRankingStrategyResult result : results) {
            csv.append(config.label()).append(',').append(result.run()).append(',').append(result.strategy()).append(',')
                .append(result.cardinality()).append(',').append(result.sourceRows()).append(',')
                .append(result.periodDays()).append(',').append(result.activeHours()).append(',')
                .append(result.pageSize()).append(',').append(number(result.elapsedMs())).append(',')
                .append(number(result.productsPerSecond())).append(',').append(result.sourceGroupQueryCount()).append(',')
                .append(result.stagingPageCount()).append(',').append(result.stagingRows()).append(',')
                .append(result.digest()).append('\n');
        }
        return csv.toString();
    }

    static String chunkCsv(PeriodRankingBenchmarkConfig config, List<PeriodRankingChunkResult> results) {
        StringBuilder csv = new StringBuilder(
            "label,run,cardinality,chunk_size,elapsed_ms,products_per_sec,staging_page_count,score_digest\n"
        );
        for (PeriodRankingChunkResult result : results) {
            csv.append(config.label()).append(',').append(result.run()).append(',').append(result.cardinality()).append(',')
                .append(result.chunkSize()).append(',').append(number(result.elapsedMs())).append(',')
                .append(number(result.productsPerSecond())).append(',').append(result.stagingPageCount()).append(',')
                .append(result.digest()).append('\n');
        }
        return csv.toString();
    }

    static String contentionCsv(PeriodRankingBenchmarkConfig config, List<PeriodRankingContentionResult> results) {
        StringBuilder csv = new StringBuilder(
            "label,run,isolation,hold_ms,snapshot_insert_ms,source_update_latency_ms,updated_rows\n"
        );
        for (PeriodRankingContentionResult result : results) {
            csv.append(config.label()).append(',').append(result.run()).append(',').append(result.isolation()).append(',')
                .append(result.holdMs()).append(',').append(number(result.snapshotInsertMs())).append(',')
                .append(number(result.sourceUpdateLatencyMs())).append(',').append(result.updatedRows()).append('\n');
        }
        return csv.toString();
    }

    static String markdown(
        PeriodRankingBenchmarkConfig config,
        List<PeriodRankingStrategyResult> strategies,
        List<PeriodRankingChunkResult> chunks,
        List<PeriodRankingContentionResult> contention,
        Environment environment,
        Instant generatedAt
    ) {
        StringBuilder report = new StringBuilder("# Period Ranking Benchmark\n\n")
            .append("Generated at: ").append(generatedAt.atZone(REPORT_ZONE)).append("\n\n")
            .append("## Environment\n\n")
            .append("- Label: `").append(config.label()).append("`\n")
            .append("- Java: `").append(environment.javaVersion()).append("`\n")
            .append("- OS: `").append(environment.os()).append("`\n")
            .append("- Database: `").append(environment.database()).append("`\n\n")
            .append("## Claim boundary\n\n")
            .append("- MySQL Testcontainers와 same-JVM JDBC 측정이며 운영 capacity 예측값이 아니다.\n")
            .append("- strategy timer에는 현재 benchmark `run_key` cleanup, aggregation, shared `RankingScoreFormula`, staging score write가 포함된다. seed, digest, report I/O는 제외한다.\n")
            .append("- legacy는 Spring Batch MySQL grouped paging SQL 형태를 재현한 비교군이며 현재 production 코드가 아니다. 각 page 조회+INSERT를 하나의 transaction으로 실행하고 source GROUP query를 다시 수행한다.\n")
            .append("- snapshot은 별도 READ_COMMITTED transaction의 source GROUP query 1회로 staging을 고정한 뒤, 각 keyset page 조회+score UPDATE를 하나의 transaction으로 수행한다.\n")
            .append("- contention은 snapshot INSERT SELECT transaction을 hold한 동안 동일 source row UPDATE 지연을 관찰한다. threshold 판정이나 운영 SLA 주장은 하지 않는다.\n")
            .append("- chunk timer는 준비된 동일 형태 snapshot의 keyset paging, score 계산, UPDATE만 포함한다. snapshot INSERT와 cleanup은 제외하며 전체 Job 시간이 아니다.\n")
            .append("- chunk 실행 순서는 run마다 회전하고 작은 표본의 중앙값만 제시한다.\n\n")
            .append("## Median summary\n\n")
            .append("### Strategy\n\n")
            .append("| Products | Strategy | Median ms | Products/s | Source GROUP queries | Staging pages |\n")
            .append("| ---: | --- | ---: | ---: | ---: | ---: |\n");
        strategyGroups(strategies).forEach((key, values) -> report.append("| ").append(key.cardinality()).append(" | ")
            .append(key.strategy()).append(" | ").append(number(median(values.stream().map(PeriodRankingStrategyResult::elapsedMs).toList())))
            .append(" | ").append(number(median(values.stream().map(PeriodRankingStrategyResult::productsPerSecond).toList())))
            .append(" | ").append(values.getFirst().sourceGroupQueryCount()).append(" | ")
            .append(values.getFirst().stagingPageCount()).append(" |\n"));
        report.append("\n### Snapshot chunk size\n\n")
            .append("| Products | Chunk | Median ms | Products/s |\n")
            .append("| ---: | ---: | ---: | ---: |\n");
        chunkGroups(chunks).forEach((key, values) -> report.append("| ").append(key.cardinality()).append(" | ")
            .append(key.chunkSize()).append(" | ").append(number(median(values.stream().map(PeriodRankingChunkResult::elapsedMs).toList())))
            .append(" | ").append(number(median(values.stream().map(PeriodRankingChunkResult::productsPerSecond).toList())))
            .append(" |\n"));
        report.append("\n### Isolation contention\n\n")
            .append("| Isolation | Median snapshot insert ms | Median source UPDATE ms |\n")
            .append("| --- | ---: | ---: |\n");
        contention.stream().collect(Collectors.groupingBy(PeriodRankingContentionResult::isolation)).forEach(
            (isolation, values) -> report.append("| ").append(isolation).append(" | ")
                .append(number(median(values.stream().map(PeriodRankingContentionResult::snapshotInsertMs).toList())))
                .append(" | ")
                .append(number(median(values.stream().map(PeriodRankingContentionResult::sourceUpdateLatencyMs).toList())))
                .append(" |\n")
        );
        report.append("\n## Strategy raw rows\n\n")
            .append("| Run | Products | Strategy | Rows | Elapsed ms | Products/s | GROUP queries | Pages | Digest |\n")
            .append("| ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (PeriodRankingStrategyResult result : strategies) {
            report.append("| ").append(result.run()).append(" | ").append(result.cardinality()).append(" | ")
                .append(result.strategy()).append(" | ").append(result.sourceRows()).append(" | ")
                .append(number(result.elapsedMs())).append(" | ").append(number(result.productsPerSecond())).append(" | ")
                .append(result.sourceGroupQueryCount()).append(" | ").append(result.stagingPageCount()).append(" | `")
                .append(result.digest()).append("` |\n");
        }
        report.append("\n## Chunk raw rows\n\n")
            .append("| Run | Products | Chunk | Elapsed ms | Products/s | Pages | Digest |\n")
            .append("| ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (PeriodRankingChunkResult result : chunks) {
            report.append("| ").append(result.run()).append(" | ").append(result.cardinality()).append(" | ")
                .append(result.chunkSize()).append(" | ").append(number(result.elapsedMs())).append(" | ")
                .append(number(result.productsPerSecond())).append(" | ").append(result.stagingPageCount())
                .append(" | `").append(result.digest()).append("` |\n");
        }
        report.append("\n## Contention raw rows\n\n")
            .append("| Run | Isolation | Hold ms | Snapshot insert ms | Source UPDATE ms | Updated rows |\n")
            .append("| ---: | --- | ---: | ---: | ---: | ---: |\n");
        for (PeriodRankingContentionResult result : contention) {
            report.append("| ").append(result.run()).append(" | ").append(result.isolation()).append(" | ")
                .append(result.holdMs()).append(" | ").append(number(result.snapshotInsertMs())).append(" | ")
                .append(number(result.sourceUpdateLatencyMs())).append(" | ").append(result.updatedRows()).append(" |\n");
        }
        return report.toString();
    }

    private static Map<StrategyKey, List<PeriodRankingStrategyResult>> strategyGroups(
        List<PeriodRankingStrategyResult> results
    ) {
        return results.stream().collect(Collectors.groupingBy(
            result -> new StrategyKey(result.cardinality(), result.strategy()),
            java.util.LinkedHashMap::new,
            Collectors.toList()
        ));
    }

    private static Map<ChunkKey, List<PeriodRankingChunkResult>> chunkGroups(List<PeriodRankingChunkResult> results) {
        return results.stream().collect(Collectors.groupingBy(
            result -> new ChunkKey(result.cardinality(), result.chunkSize()),
            java.util.LinkedHashMap::new,
            Collectors.toList()
        ));
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    record Environment(String javaVersion, String os, String database) {
    }

    record ReportFiles(Path strategyCsv, Path chunkCsv, Path contentionCsv, Path markdown) {
        void verifyWritten() {
            if (!Files.isRegularFile(strategyCsv) || !Files.isRegularFile(chunkCsv)
                || !Files.isRegularFile(contentionCsv) || !Files.isRegularFile(markdown)) {
                throw new IllegalStateException("period ranking benchmark report was not written");
            }
        }
    }

    private record StrategyKey(int cardinality, String strategy) {
    }

    private record ChunkKey(int cardinality, int chunkSize) {
    }
}
