package com.loopers.batch.job.ranking;

import com.loopers.application.ranking.ProductMetricInput;
import com.loopers.application.ranking.ProductRankAggregationProcessor;
import com.loopers.application.ranking.ProductRankSnapshotRepository;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@StepScope
@RequiredArgsConstructor
public class ProductRankSnapshotItemWriter implements ItemWriter<ProductMetricInput>, StepExecutionListener {

    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final ProductRankAggregationProcessor aggregationProcessor;
    private final ProductRankSnapshotRepository snapshotRepository;

    @Value("#{jobParameters['period']}")
    private String period;

    @Value("#{jobParameters['startDate']}")
    private String startDate;

    @Value("#{jobParameters['endDate']}")
    private String endDate;

    private final List<ProductMetricInput> metrics = new ArrayList<>();
    private StepExecution stepExecution;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void write(Chunk<? extends ProductMetricInput> chunk) {
        metrics.addAll(chunk.getItems());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        Long batchRunId = this.stepExecution.getJobExecution().getExecutionContext().getLong("batchRunId");
        List<ProductRankSnapshot> snapshots = aggregationProcessor.aggregate(
            RankingPeriod.valueOf(period),
            LocalDate.parse(startDate, DATE_KEY_FORMATTER),
            LocalDate.parse(endDate, DATE_KEY_FORMATTER),
            batchRunId,
            metrics
        );
        snapshotRepository.saveAll(snapshots);
        return stepExecution.getExitStatus();
    }
}
