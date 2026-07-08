package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisWaitingQueueRepositoryIntegrationTest {

    private final WaitingQueueRepository waitingQueueRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public RedisWaitingQueueRepositoryIntegrationTest(
        WaitingQueueRepository waitingQueueRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("먼저 진입한 유저가 더 앞 순번(0-based)을 받는다.")
    @Test
    void assignsPositionsInEnrollmentOrder() {
        // given
        waitingQueueRepository.enroll(200L);   // 먼저 진입
        waitingQueueRepository.enroll(100L);   // 나중 진입

        // when
        Optional<QueuePosition> first = waitingQueueRepository.positionOf(200L);
        Optional<QueuePosition> second = waitingQueueRepository.positionOf(100L);

        // then
        assertThat(first).contains(QueuePosition.of(0L));
        assertThat(second).contains(QueuePosition.of(1L));
    }

    @DisplayName("이미 대기 중인 유저가 다시 진입해도 순번과 전체 대기 인원은 그대로다.")
    @Test
    void keepsPositionWhenSameUserReenrolls() {
        // given
        waitingQueueRepository.enroll(200L);
        waitingQueueRepository.enroll(100L);

        // when
        QueuePosition reenrolled = waitingQueueRepository.enroll(200L);   // 새로고침으로 인한 재진입

        // then
        assertThat(reenrolled.value()).isEqualTo(0L);
        assertThat(waitingQueueRepository.positionOf(200L)).contains(QueuePosition.of(0L));
        assertThat(waitingQueueRepository.size()).isEqualTo(2L);
    }

    @DisplayName("대기열에 없는 유저의 순번 조회는 빈 결과를 준다.")
    @Test
    void returnsEmptyForUserNotInQueue() {
        // given
        waitingQueueRepository.enroll(100L);

        // when
        Optional<QueuePosition> position = waitingQueueRepository.positionOf(999L);

        // then
        assertThat(position).isEmpty();
    }

    @DisplayName("전체 대기 인원은 진입한 서로 다른 유저 수와 같다.")
    @Test
    void countsAllWaitingUsers() {
        // given
        waitingQueueRepository.enroll(1L);
        waitingQueueRepository.enroll(2L);
        waitingQueueRepository.enroll(3L);

        // when
        long size = waitingQueueRepository.size();

        // then
        assertThat(size).isEqualTo(3L);
    }

    @DisplayName("앞에서 N명을 꺼내면 먼저 진입한 순서대로 반환되고 대기열에서 제거된다.")
    @Test
    void pollNextRemovesFrontUsersInEnrollmentOrder() {
        // given
        waitingQueueRepository.enroll(100L);
        waitingQueueRepository.enroll(200L);
        waitingQueueRepository.enroll(300L);

        // when
        List<Long> polled = waitingQueueRepository.pollNext(2);

        // then
        assertThat(polled).containsExactly(100L, 200L);
        assertThat(waitingQueueRepository.size()).isEqualTo(1L);
    }
}
