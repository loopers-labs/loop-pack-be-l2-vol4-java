package com.loopers.config.scheduler;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 + 분산 락 활성화.
 * <p>
 * 여러 commerce-api 인스턴스가 같은 {@code @Scheduled} 작업을 동시에 실행하면 같은 PENDING 결제를
 * 중복으로 reconcile(불필요한 PG 호출)하게 된다. 정합성 자체는 {@code PaymentConfirmer}의 비관 락+멱등이
 * 보장하지만, 낭비를 막기 위해 ShedLock으로 회차당 한 인스턴스만 실행되도록 직렬화한다.
 * <p>
 * 락 상태는 {@code shedlock} 테이블(JDBC)로 공유한다. 시계 차로 인한 락 만료 오차를 피하려고 DB 서버
 * 시간({@code usingDbTime()})을 기준으로 한다. {@code defaultLockAtMostFor}는 작업이 락을 풀지 못하고
 * 죽었을 때의 안전 상한(개별 작업에서 {@code lockAtMostFor}로 덮어쓴다).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
