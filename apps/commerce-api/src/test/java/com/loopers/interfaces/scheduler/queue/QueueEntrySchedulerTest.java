package com.loopers.interfaces.scheduler.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueThroughputPolicy;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueEntrySchedulerTest {

    @Autowired
    private QueueEntryScheduler queueEntryScheduler;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 인원이 배치 크기보다 많으면,")
    @Test
    void issueTokens_popsOnlyBatchSizeUsers_andIssuesTokens() {
        // arrange
        int totalUsers = QueueThroughputPolicy.BATCH_SIZE + 5;
        for (long userId = 1; userId <= totalUsers; userId++) {
            queueRepository.enter(userId, System.currentTimeMillis() + userId);
        }

        // act
        queueEntryScheduler.issueTokens();

        // assert
        int issuedCount = 0;
        for (long userId = 1; userId <= totalUsers; userId++) {
            if (entryTokenRepository.find(userId).isPresent()) {
                issuedCount++;
            }
        }
        assertThat(issuedCount).isEqualTo(QueueThroughputPolicy.BATCH_SIZE);
        assertThat(queueRepository.size()).isEqualTo(5L);
    }
}
