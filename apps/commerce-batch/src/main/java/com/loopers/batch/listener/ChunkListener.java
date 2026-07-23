package com.loopers.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChunkListener {

  private static final String START_NANOS = ChunkListener.class.getName() + ".startNanos";
  private static final String START_READ_COUNT = ChunkListener.class.getName() + ".startReadCount";
  private static final String START_WRITE_COUNT =
      ChunkListener.class.getName() + ".startWriteCount";

  private final int logInterval;

  public ChunkListener(@Value("${product-ranking.batch.chunk-log-interval:1}") int logInterval) {
    if (logInterval < 0) {
      throw new IllegalArgumentException("product-ranking.batch.chunk-log-interval은 0 이상이어야 합니다.");
    }
    this.logInterval = logInterval;
  }

  @BeforeChunk
  void beforeChunk(ChunkContext chunkContext) {
    var stepExecution = chunkContext.getStepContext().getStepExecution();
    chunkContext.setAttribute(START_NANOS, System.nanoTime());
    chunkContext.setAttribute(START_READ_COUNT, stepExecution.getReadCount());
    chunkContext.setAttribute(START_WRITE_COUNT, stepExecution.getWriteCount());
  }

  @AfterChunk
  void afterChunk(ChunkContext chunkContext) {
    if (logInterval == 0) {
      return;
    }

    var stepExecution = chunkContext.getStepContext().getStepExecution();
    long commitCount = stepExecution.getCommitCount();
    if (commitCount % logInterval != 0) {
      return;
    }

    long startNanos = (long) chunkContext.getAttribute(START_NANOS);
    long startReadCount = (long) chunkContext.getAttribute(START_READ_COUNT);
    long startWriteCount = (long) chunkContext.getAttribute(START_WRITE_COUNT);
    long durationNanos = System.nanoTime() - startNanos;

    log.info(
        "PERF_CHUNK step={}, commitCount={}, durationMs={}, readDelta={}, writeDelta={}, "
            + "readCount={}, writeCount={}",
        stepExecution.getStepName(),
        commitCount,
        durationNanos / 1_000_000.0,
        stepExecution.getReadCount() - startReadCount,
        stepExecution.getWriteCount() - startWriteCount,
        stepExecution.getReadCount(),
        stepExecution.getWriteCount());
  }
}
