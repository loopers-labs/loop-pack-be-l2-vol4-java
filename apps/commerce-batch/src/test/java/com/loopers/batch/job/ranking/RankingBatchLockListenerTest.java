package com.loopers.batch.job.ranking;

import com.loopers.domain.ranking.batch.RankingBatchLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RankingBatchLockListenerTest {

    private static class RecordingRankingBatchLock implements RankingBatchLock {
        private final boolean tryLockResult;
        final List<String> lockedKeys = new ArrayList<>();
        final List<String> unlockedKeys = new ArrayList<>();

        private RecordingRankingBatchLock(boolean tryLockResult) {
            this.tryLockResult = tryLockResult;
        }

        @Override
        public boolean tryLock(String key, String token, Duration ttl) {
            if (tryLockResult) {
                lockedKeys.add(key);
            }
            return tryLockResult;
        }

        @Override
        public void unlock(String key, String token) {
            unlockedKeys.add(key);
        }
    }

    private JobExecution jobExecution(String period, String periodKey) {
        var parameters = new JobParametersBuilder()
            .addString("period", period)
            .addString("periodKey", periodKey)
            .toJobParameters();
        return new JobExecution(new JobInstance(1L, "rankingProductMvJob"), 1L, parameters);
    }

    @DisplayName("beforeJob()을 호출할 때,")
    @Nested
    class BeforeJob {

        @DisplayName("락을 획득하면 예외 없이 통과한다.")
        @Test
        void passes_whenLockIsAcquired() {
            var lock = new RecordingRankingBatchLock(true);
            var listener = new RankingBatchLockListener(lock);
            var execution = jobExecution("WEEKLY", "2026W30");

            listener.beforeJob(execution);

            assertThat(lock.lockedKeys).containsExactly("ranking:batch:lock:WEEKLY:2026W30");
        }

        @DisplayName("락을 획득하지 못하면 IllegalStateException을 던진다.")
        @Test
        void throwsIllegalStateException_whenLockIsNotAcquired() {
            var lock = new RecordingRankingBatchLock(false);
            var listener = new RankingBatchLockListener(lock);
            var execution = jobExecution("WEEKLY", "2026W30");

            assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> listener.beforeJob(execution));
        }
    }

    @DisplayName("afterJob()을 호출할 때,")
    @Nested
    class AfterJob {

        @DisplayName("beforeJob에서 락을 획득했다면 같은 키로 unlock한다.")
        @Test
        void unlocks_whenLockWasAcquiredInBeforeJob() {
            var lock = new RecordingRankingBatchLock(true);
            var listener = new RankingBatchLockListener(lock);
            var execution = jobExecution("WEEKLY", "2026W30");

            listener.beforeJob(execution);
            listener.afterJob(execution);

            assertThat(lock.unlockedKeys).containsExactly("ranking:batch:lock:WEEKLY:2026W30");
        }

        @DisplayName("beforeJob에서 락을 얻지 못했다면 unlock을 호출하지 않는다.")
        @Test
        void doesNotUnlock_whenLockWasNotAcquiredInBeforeJob() {
            var lock = new RecordingRankingBatchLock(false);
            var listener = new RankingBatchLockListener(lock);
            var execution = jobExecution("WEEKLY", "2026W30");

            try {
                listener.beforeJob(execution);
            } catch (IllegalStateException ignored) {
                // beforeJob 실패 시에도 Spring Batch가 afterJob을 호출하는 상황을 재현
            }
            listener.afterJob(execution);

            assertThat(lock.unlockedKeys).isEmpty();
        }
    }
}