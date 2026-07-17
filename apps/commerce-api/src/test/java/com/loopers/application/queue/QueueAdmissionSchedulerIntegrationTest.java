package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.infrastructure.queue.QueueProperties;
import com.loopers.utils.RedisCleanUp;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// test 프로필에서는 queue.scheduler-enabled=false 로 자동 tick 이 꺼져 있다(application.yml).
// 이 테스트는 admit()을 직접 호출해 검증하므로 자동 실행과 경합할 일이 없다.
@SpringBootTest
class QueueAdmissionSchedulerIntegrationTest {

    @Autowired
    private QueueAdmissionScheduler queueAdmissionScheduler;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 배치 발급을 실행할 때,")
    @Nested
    class Admit {

        @DisplayName("batchSize() 만큼만 토큰이 발급되고 대기열에서 빠진다.")
        @Test
        void admit_issuesTokensForBatchSizeUsers_andRemovesThemFromWaitingQueue() {
            // given
            int totalUsers = queueProperties.batchSize() + 3;
            for (long userId = 1; userId <= totalUsers; userId++) {
                waitingQueueRepository.enter(userId, userId * 1000);
            }

            // when
            queueAdmissionScheduler.admit();

            // then
            long admittedCount = 0;
            for (long userId = 1; userId <= totalUsers; userId++) {
                if (entryTokenRepository.find(userId).isPresent()) {
                    admittedCount++;
                }
            }
            final long finalAdmittedCount = admittedCount;
            assertAll(
                    () -> assertThat(finalAdmittedCount).isEqualTo(queueProperties.batchSize()),
                    () -> assertThat(waitingQueueRepository.size()).isEqualTo(totalUsers - queueProperties.batchSize())
            );
        }

        @DisplayName("실행 후 gauge 값이 실행 시각 근접 값으로 갱신된다.")
        @Test
        void admit_updatesLastExecutionTimestampGauge_toCurrentTime() {
            // given
            long before = Instant.now().toEpochMilli();

            // when
            queueAdmissionScheduler.admit();

            // then
            long after = Instant.now().toEpochMilli();
            Gauge gauge = meterRegistry.get("queue.scheduler.last.execution.timestamp").gauge();
            assertThat(gauge.value()).isBetween((double) before, (double) after);
        }
    }
}
