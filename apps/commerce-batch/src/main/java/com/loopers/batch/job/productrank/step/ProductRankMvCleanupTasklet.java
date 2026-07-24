package com.loopers.batch.job.productrank.step;

import com.loopers.batch.job.productrank.ProductRankMvTable;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

public class ProductRankMvCleanupTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate asOfDate;
    private final ProductRankMvTable table;

    public ProductRankMvCleanupTasklet(JdbcTemplate jdbcTemplate, LocalDate asOfDate, ProductRankMvTable table) {
        this.jdbcTemplate = jdbcTemplate;
        this.asOfDate = asOfDate;
        this.table = table;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        jdbcTemplate.update("DELETE FROM %s WHERE as_of_date = ?".formatted(table.tableName()), asOfDate);
        return RepeatStatus.FINISHED;
    }
}
