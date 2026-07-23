package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.RankingSnapshotTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일간 랭킹 스냅샷 잡 — 확정된 하루치 ZSET 을 ranking_daily_snapshot 으로 내린다(week9).
 *
 * <p>실행: {@code --job.name=rankingSnapshotJob} (+ 선택 {@code snapshotDate=yyyyMMdd}, 없으면 어제 KST).
 * 하루 1회, 자정 이후 어제 키가 TTL(2일) 안에 살아있을 때 돌리면 된다.
 *
 * <p>Demo 잡과 달리 {@code ResourcelessTransactionManager} 가 아니라 <b>실제 트랜잭션 매니저</b>를 쓴다 —
 * tasklet 이 DB 에 쓰기 때문이다(delete-then-insert 가 한 트랜잭션으로 묶여야 중간 상태가 보이지 않는다).
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingSnapshotJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingSnapshotJobConfig {

    public static final String JOB_NAME = "rankingSnapshotJob";
    private static final String STEP_NAME = "rankingSnapshotStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final RankingSnapshotTasklet rankingSnapshotTasklet;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job rankingSnapshotJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(rankingSnapshotStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step rankingSnapshotStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(rankingSnapshotTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
