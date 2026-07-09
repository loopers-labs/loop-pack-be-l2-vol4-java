package com.loopers.benchmark.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class WaitingQueueBenchmarkReportTest {

    @TempDir
    Path tempDir;

    @DisplayName("시나리오와 요청 원본을 timestamped CSV와 비교 Markdown으로 기록한다.")
    @Test
    void writesTimestampedCsvAndMarkdownReports() throws IOException {
        // arrange
        WaitingQueueBenchmarkConfig config = new WaitingQueueBenchmarkConfig(
            2,
            2,
            List.of(5, 10),
            List.of(100L),
            2,
            1,
            10,
            tempDir,
            "ci run"
        );
        WaitingQueueBenchmarkEnvironment environment = new WaitingQueueBenchmarkEnvironment(
            "21",
            "macOS",
            "aarch64",
            8,
            "Asia/Seoul",
            "test",
            49152
        );
        List<WaitingQueueBenchmarkScenario> scenarios = List.of(
            scenario(5, 1, 18.5, 42.0),
            scenario(5, 2, 18.5, 70.0),
            scenario(10, 1, 24.0, 31.0),
            scenario(10, 2, 24.0, 30.0)
        );

        // act
        WaitingQueueBenchmarkReport.ReportFiles reportFiles = WaitingQueueBenchmarkReport.write(
            config,
            environment,
            scenarios,
            Instant.parse("2026-07-10T01:02:03.456Z")
        );

        // assert
        String markdown = Files.readString(reportFiles.markdown());
        String scenariosCsv = Files.readString(reportFiles.scenariosCsv());
        String requestsCsv = Files.readString(reportFiles.requestsCsv());
        assertAll(
            () -> assertThat(reportFiles.scenariosCsv().getFileName().toString())
                .startsWith("20260710-100203-456-ci-run"),
            () -> assertThat(reportFiles.requestsCsv()).isRegularFile(),
            () -> assertThat(reportFiles.markdown()).isRegularFile(),
            () -> assertThat(scenariosCsv)
                .contains("batch_size,admit_delay_ms,run")
                .contains("attempted_throughput_tps,successful_throughput_tps")
                .contains("avg_latency_ms")
                .contains("observed_admission_tps")
                .contains("persisted_outbox_events")
                .contains("effective_hikari_max,effective_hikari_min"),
            () -> assertThat(requestsCsv).contains("user_login_id,http_status,success,latency_ms,failure"),
            () -> assertThat(markdown).contains("## Environment and configuration"),
            () -> assertThat(markdown).contains("## Raw scenario results"),
            () -> assertThat(markdown).contains("## Aggregated batch/delay comparison"),
            () -> assertThat(markdown).contains("pooled raw request latencies across runs"),
            () -> assertThat(markdown).contains("Attempted TPS | Successful TPS"),
            () -> assertThat(markdown)
                .contains("| 10 | 100 | 2 | 2 / 4 | 50.000 | 24.000 | 12.000 | 100.000 | 28.571 | 25.250 |"),
            () -> assertThat(markdown).contains("Best successful throughput: batch 10 / delay 100 ms"),
            () -> assertThat(markdown).contains("Lowest p95: batch 10 / delay 100 ms"),
            () -> assertThat(scenarios.get(0).attemptedThroughputTps()).isEqualTo(18.5),
            () -> assertThat(scenarios.get(0).successfulThroughputTps()).isEqualTo(9.25),
            () -> assertThat(scenarios.get(0).avgLatencyMs()).isEqualTo(31.0),
            () -> assertThat(markdown)
                .contains("Local same-JVM/Testcontainers values are comparative, not production capacity guarantees.")
        );
    }

    private WaitingQueueBenchmarkScenario scenario(
        int batchSize,
        int run,
        double attemptedThroughputTps,
        double p95Ms
    ) {
        List<WaitingQueueBenchmarkRequest> requests = List.of(
            new WaitingQueueBenchmarkRequest("qb00000001", 200, true, 20.0, ""),
            new WaitingQueueBenchmarkRequest("qb00000002", 503, false, p95Ms, "HTTP 503")
        );
        double e2eDurationMs = 2_000.0 / attemptedThroughputTps;
        return WaitingQueueBenchmarkScenario.completed(
            batchSize,
            100L,
            run,
            2,
            2,
            10,
            10,
            5,
            e2eDurationMs + 40.0,
            e2eDurationMs,
            70.0,
            0L,
            1L,
            1L,
            1L,
            7,
            2,
            requests
        );
    }
}
