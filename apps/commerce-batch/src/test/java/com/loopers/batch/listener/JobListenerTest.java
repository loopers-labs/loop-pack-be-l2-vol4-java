package com.loopers.batch.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * afterJob 은 성공·실패 어느 쪽에서도 예외를 던지지 않는다 — 알림 처리가 잡 결과를 덮어써선 안 된다.
 * 실패 알림 자체(error 로그 → Slack appender)는 부수효과라 여기선 안전한 통과만 확인한다.
 */
class JobListenerTest {

    private final JobListener listener = new JobListener();

    private JobExecution execution(BatchStatus status) {
        JobExecution execution = new JobExecution(new JobInstance(1L, "weeklyRankingJob"), new JobParameters());
        execution.getExecutionContext().putLong("startTime", System.currentTimeMillis());
        execution.setStatus(status);
        if (status == BatchStatus.FAILED) {
            execution.addFailureException(new RuntimeException("집계 실패"));
        }
        return execution;
    }

    @Test
    @DisplayName("실패한 잡의 afterJob 은 실패 사유를 알리되 예외를 던지지 않는다")
    void givenFailedJob_whenAfterJob_thenNotifiesWithoutThrowing() {
        assertThatCode(() -> listener.afterJob(execution(BatchStatus.FAILED)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공한 잡의 afterJob 은 알림 없이 정상 종료한다")
    void givenCompletedJob_whenAfterJob_thenNoNotification() {
        assertThatCode(() -> listener.afterJob(execution(BatchStatus.COMPLETED)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실패 사유가 없어도(빈 예외 목록) 터지지 않는다")
    void givenFailedWithoutExceptions_whenAfterJob_thenStillSafe() {
        JobExecution execution = new JobExecution(new JobInstance(1L, "weeklyRankingJob"), new JobParameters());
        execution.getExecutionContext().putLong("startTime", System.currentTimeMillis());
        execution.setStatus(BatchStatus.FAILED);

        assertThatCode(() -> listener.afterJob(execution)).doesNotThrowAnyException();
    }
}
