package com.loopers.queue.infrastructure;

import com.loopers.queue.domain.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class RedisWaitingQueueRepositoryIntegrationTest {

    @Autowired
    private WaitingQueueRepository sut;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("빈 대기열에 처음 진입하면 순번은 0이다")
    void givenEmptyQueue_whenAddFirstUser_thenRankIsZero() {
        sut.add("user-1", 1000L);

        assertThat(sut.rank("user-1")).isEqualTo(0L);
    }

    @Test
    @DisplayName("먼저 진입한 유저가 더 앞 순번을 가진다")
    void givenTwoUsers_whenEnterInOrder_thenEarlierHasLowerRank() {
        sut.add("user-1", 1000L);
        sut.add("user-2", 2000L);

        assertAll(
                () -> assertThat(sut.rank("user-1")).isEqualTo(0L),
                () -> assertThat(sut.rank("user-2")).isEqualTo(1L)
        );
    }

    @Test
    @DisplayName("이미 대기 중인 유저가 재진입해도 순번은 유지된다 (ZADD NX)")
    void givenUserAlreadyWaiting_whenReEnterWithLaterScore_thenRankUnchanged() {
        sut.add("user-1", 1000L);
        sut.add("user-2", 2000L);

        boolean reAdded = sut.add("user-1", 3000L);

        assertAll(
                () -> assertThat(reAdded).isFalse(),
                () -> assertThat(sut.rank("user-1")).isEqualTo(0L)
        );
    }

    @Test
    @DisplayName("전체 대기 인원을 조회한다")
    void givenUsersInQueue_whenSize_thenReturnsCount() {
        sut.add("user-1", 1000L);
        sut.add("user-2", 2000L);

        assertThat(sut.size()).isEqualTo(2L);
    }
}
