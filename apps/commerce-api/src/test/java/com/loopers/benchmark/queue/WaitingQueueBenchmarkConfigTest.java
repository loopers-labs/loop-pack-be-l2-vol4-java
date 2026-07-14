package com.loopers.benchmark.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class WaitingQueueBenchmarkConfigTest {

    @DisplayName("설정이 없으면 대기열 벤치마크 기본값을 사용한다.")
    @Test
    void usesDefaults_whenPropertiesAreEmpty() {
        // arrange
        Map<String, String> properties = Map.of();

        // act
        WaitingQueueBenchmarkConfig config = WaitingQueueBenchmarkConfig.from(properties);

        // assert
        assertAll(
            () -> assertThat(config.users()).isEqualTo(60),
            () -> assertThat(config.concurrency()).isEqualTo(20),
            () -> assertThat(config.batchSizes()).containsExactly(5, 10, 18),
            () -> assertThat(config.admitDelaysMs()).containsExactly(100L),
            () -> assertThat(config.runs()).isEqualTo(2),
            () -> assertThat(config.warmupUsers()).isEqualTo(10),
            () -> assertThat(config.dbPoolSize()).isEqualTo(10),
            () -> assertThat(config.outputDir()).isEqualTo(Path.of("build/reports/waiting-queue")),
            () -> assertThat(config.label()).isEqualTo("local"),
            () -> assertThat(config.expectedScenarioCount()).isEqualTo(6)
        );
    }

    @DisplayName("Gradle 속성의 단일 값과 쉼표 목록을 벤치마크 설정으로 변환한다.")
    @Test
    void parsesCustomValues_whenPropertiesAreProvided() {
        // arrange
        Map<String, String> properties = Map.of(
            "queueBenchmarkUsers", "120",
            "queueBenchmarkConcurrency", "30",
            "queueBenchmarkBatchSizes", " 8, 16 ",
            "queueBenchmarkAdmitDelaysMs", "0, 250",
            "queueBenchmarkRuns", "3",
            "queueBenchmarkWarmupUsers", "0",
            "queueBenchmarkDbPoolSize", "12",
            "queueBenchmarkOutputDir", "custom/report",
            "queueBenchmarkLabel", "pool-12"
        );

        // act
        WaitingQueueBenchmarkConfig config = WaitingQueueBenchmarkConfig.from(properties);

        // assert
        assertAll(
            () -> assertThat(config.users()).isEqualTo(120),
            () -> assertThat(config.concurrency()).isEqualTo(30),
            () -> assertThat(config.batchSizes()).containsExactly(8, 16),
            () -> assertThat(config.admitDelaysMs()).containsExactly(0L, 250L),
            () -> assertThat(config.runs()).isEqualTo(3),
            () -> assertThat(config.warmupUsers()).isZero(),
            () -> assertThat(config.dbPoolSize()).isEqualTo(12),
            () -> assertThat(config.outputDir()).isEqualTo(Path.of("custom/report")),
            () -> assertThat(config.label()).isEqualTo("pool-12"),
            () -> assertThat(config.expectedScenarioCount()).isEqualTo(12)
        );
    }

    @DisplayName("실행을 왜곡하는 잘못된 수치 설정은 거부한다.")
    @Test
    void rejectsInvalidValues() {
        // arrange
        Map<String, String> properties = Map.of(
            "queueBenchmarkUsers", "0",
            "queueBenchmarkBatchSizes", "5,0"
        );

        // act & assert
        assertThatThrownBy(() -> WaitingQueueBenchmarkConfig.from(properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("queueBenchmarkUsers");
    }
}
