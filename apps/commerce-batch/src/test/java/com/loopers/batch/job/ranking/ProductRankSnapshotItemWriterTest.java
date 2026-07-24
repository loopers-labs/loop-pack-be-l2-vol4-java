package com.loopers.batch.job.ranking;

import com.loopers.application.ranking.ProductMetricInput;
import com.loopers.application.ranking.ProductRankAggregationProcessor;
import com.loopers.application.ranking.ProductRankSnapshotRepository;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRankSnapshotItemWriterTest {

    @Test
    @DisplayName("write로 누적한 metrics를 yyyyMMdd 파라미터 기준으로 집계해 Snapshot으로 저장한다.")
    void afterStep_ShouldAggregateWrittenMetricsWithDateKeyParameters() throws Exception {
        ProductRankAggregationProcessor aggregationProcessor = mock(ProductRankAggregationProcessor.class);
        ProductRankSnapshotRepository snapshotRepository = mock(ProductRankSnapshotRepository.class);
        ProductRankSnapshotItemWriter writer = new ProductRankSnapshotItemWriter(
            aggregationProcessor,
            snapshotRepository
        );
        ReflectionTestUtils.setField(writer, "period", "WEEKLY");
        ReflectionTestUtils.setField(writer, "startDate", "20260720");
        ReflectionTestUtils.setField(writer, "endDate", "20260726");

        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.getExecutionContext().putLong("batchRunId", 10L);
        StepExecution stepExecution = new StepExecution("aggregateProductRankStep", jobExecution);
        writer.beforeStep(stepExecution);
        ProductMetricInput firstMetric = new ProductMetricInput(LocalDate.of(2026, 7, 20), 1L, 10.0);
        ProductMetricInput secondMetric = new ProductMetricInput(LocalDate.of(2026, 7, 21), 1L, 20.0);
        ProductRankSnapshot snapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            10L,
            1L,
            1,
            30.0
        );
        when(aggregationProcessor.aggregate(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            10L,
            List.of(firstMetric, secondMetric)
        )).thenReturn(List.of(snapshot));

        writer.write(Chunk.of(firstMetric));
        writer.write(Chunk.of(secondMetric));
        writer.afterStep(stepExecution);

        verify(snapshotRepository).saveAll(List.of(snapshot));
    }
}
