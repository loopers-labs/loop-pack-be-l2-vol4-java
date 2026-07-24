package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenDlqRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisEntryTokenDlqRepositoryIntegrationTest {

    @Autowired
    private EntryTokenDlqRepository entryTokenDlqRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("push한 순서대로(FIFO) drain된다.")
    @Test
    void drainsInFifoOrder() {
        // arrange
        entryTokenDlqRepository.push(1L);
        entryTokenDlqRepository.push(2L);
        entryTokenDlqRepository.push(3L);

        // act
        List<Long> drained = entryTokenDlqRepository.drain(2);

        // assert
        assertThat(drained).containsExactly(1L, 2L);
        // 남은 하나만 이어서 drain
        assertThat(entryTokenDlqRepository.drain(10)).containsExactly(3L);
    }

    @DisplayName("빈 실패함을 drain하면 빈 목록을 반환한다.")
    @Test
    void returnsEmpty_whenNothingQueued() {
        assertThat(entryTokenDlqRepository.drain(5)).isEmpty();
    }
}
