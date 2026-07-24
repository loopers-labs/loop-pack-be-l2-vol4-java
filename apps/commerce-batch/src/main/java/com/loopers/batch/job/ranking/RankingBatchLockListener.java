package com.loopers.batch.job.ranking;

import com.loopers.domain.ranking.batch.RankingBatchLock;
import com.loopers.domain.ranking.batch.RankingBatchLockKeys;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 같은 (period, periodKey) 조합에 대해 rankingProductMvJob이 동시에 두 번 실행되는 것을 막는다.
 * 락을 얻지 못하면 beforeJob에서 예외를 던져 Job을 시작 전에 FAILED 처리한다.
 * 이 listener의 beforeJob이 예외를 던진 경우에도 Spring Batch는 등록된 모든 listener의 afterJob을
 * 호출하므로, ExecutionContext에 key/token을 기록해뒀는지로 "내가 락을 잡았었는지"를 판단해
 * 다른 실행이 잡고 있는 락을 실수로 해제하지 않도록 한다.
 */
@RequiredArgsConstructor
@Component
public class RankingBatchLockListener implements JobExecutionListener {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final String CONTEXT_KEY = "rankingBatchLockKey";
    private static final String CONTEXT_TOKEN = "rankingBatchLockToken";

    private final RankingBatchLock lock;

    @Override
    public void beforeJob(@Nonnull JobExecution jobExecution) {
        var parameters = jobExecution.getJobParameters();
        String key = RankingBatchLockKeys.of(parameters.getString("period"), parameters.getString("periodKey"));
        String token = UUID.randomUUID().toString();

        if (!lock.tryLock(key, token, LOCK_TTL)) {
            throw new IllegalStateException("이미 실행 중인 랭킹 배치가 있습니다: " + key);
        }

        jobExecution.getExecutionContext().putString(CONTEXT_KEY, key);
        jobExecution.getExecutionContext().putString(CONTEXT_TOKEN, token);
    }

    @Override
    public void afterJob(@Nonnull JobExecution jobExecution) {
        var context = jobExecution.getExecutionContext();
        if (!context.containsKey(CONTEXT_KEY)) {
            return;
        }
        lock.unlock(context.getString(CONTEXT_KEY), context.getString(CONTEXT_TOKEN));
    }
}