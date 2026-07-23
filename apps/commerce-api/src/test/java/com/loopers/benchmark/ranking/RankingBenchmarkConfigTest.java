package com.loopers.benchmark.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingBenchmarkConfigTest {
    @DisplayName("랭킹 벤치마크 설정을 시스템 속성 형식에서 읽는다.")
    @Test
    void parsesConfiguration() {
        RankingBenchmarkConfig config = RankingBenchmarkConfig.from(Map.of(
            "rankingBenchmarkCardinalities", "1000, 10000",
            "rankingBenchmarkPageSizes", "20,100",
            "rankingBenchmarkConcurrency", "25",
            "rankingBenchmarkIterations", "300",
            "rankingBenchmarkWarmup", "30",
            "rankingBenchmarkRuns", "3",
            "rankingBenchmarkOutputDir", "custom/ranking",
            "rankingBenchmarkLabel", "local m2"
        ));

        assertAll(
            () -> assertThat(config.cardinalities()).containsExactly(1000, 10000),
            () -> assertThat(config.pageSizes()).containsExactly(20, 100),
            () -> assertThat(config.concurrency()).isEqualTo(25),
            () -> assertThat(config.iterations()).isEqualTo(300),
            () -> assertThat(config.warmup()).isEqualTo(30),
            () -> assertThat(config.runs()).isEqualTo(3),
            () -> assertThat(config.outputDir()).isEqualTo(Path.of("custom/ranking")),
            () -> assertThat(config.label()).isEqualTo("local m2"),
            () -> assertThat(config.expectedResultCount()).isEqualTo(24)
        );
    }

    @DisplayName("API 제한을 넘는 페이지 크기는 거부한다.")
    @Test
    void rejectsPageSizeOverApiLimit() {
        assertThatThrownBy(() -> RankingBenchmarkConfig.from(Map.of("rankingBenchmarkPageSizes", "101")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rankingBenchmarkPageSizes");
    }

    @DisplayName("랭킹 데이터보다 큰 페이지 크기는 완전한 응답 검증이 불가능하므로 거부한다.")
    @Test
    void rejectsPageLargerThanCardinality() {
        assertThatThrownBy(() -> RankingBenchmarkConfig.from(Map.of(
            "rankingBenchmarkCardinalities", "10",
            "rankingBenchmarkPageSizes", "20"
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rankingBenchmarkCardinalities");
    }
}
