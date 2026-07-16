package com.loopers.domain.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdmissionServiceIntegrationTest {

    private final AdmissionService admissionService;
    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public AdmissionServiceIntegrationTest(
        AdmissionService admissionService,
        WaitingQueueRepository waitingQueueRepository,
        EntryTokenRepository entryTokenRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.admissionService = admissionService;
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 앞에서 batchSize 명을 입장시키면, 그 인원에게 토큰이 발급되고 대기열에서 빠진다.")
    @Test
    void admitsFrontUsersAndIssuesTokens() {
        // given
        waitingQueueRepository.enroll(100L);
        waitingQueueRepository.enroll(200L);
        waitingQueueRepository.enroll(300L);

        // when
        int admitted = admissionService.admit(2);

        // then
        assertThat(admitted).isEqualTo(2);
        assertThat(entryTokenRepository.find(100L)).isPresent();
        assertThat(entryTokenRepository.find(200L)).isPresent();
        assertThat(entryTokenRepository.find(300L)).isEmpty();
        assertThat(waitingQueueRepository.size()).isEqualTo(1L);
        assertThat(waitingQueueRepository.positionOf(300L)).contains(QueuePosition.of(0L));
    }

    @DisplayName("배치 크기가 대기 인원보다 크면, 대기 중인 전원만 입장시킨다.")
    @Test
    void admitsOnlyAvailableWhenBatchLargerThanQueue() {
        // given
        waitingQueueRepository.enroll(100L);

        // when
        int admitted = admissionService.admit(5);

        // then
        assertThat(admitted).isEqualTo(1);
        assertThat(entryTokenRepository.find(100L)).isPresent();
        assertThat(waitingQueueRepository.size()).isZero();
    }

    @DisplayName("대기열이 비어 있으면 아무도 입장시키지 않는다.")
    @Test
    void admitsNoneWhenQueueEmpty() {
        // when
        int admitted = admissionService.admit(2);

        // then
        assertThat(admitted).isZero();
    }

    @DisplayName("유입이 배치 크기보다 많아도 한 번의 입장 처리는 배치 크기만큼만 흘려보낸다.")
    @Test
    void admitsAtMostBatchSizeWhenInflowExceedsBatch() {
        // given - 배치(5)보다 많은 12명 유입
        for (long userId = 1; userId <= 12; userId++) {
            waitingQueueRepository.enroll(userId);
        }

        // when
        int admitted = admissionService.admit(5);

        // then - 5명만 입장, 나머지 7명은 대기열에 안정적으로 잔류 (back-pressure)
        assertThat(admitted).isEqualTo(5);
        assertThat(waitingQueueRepository.size()).isEqualTo(7L);
    }

    @DisplayName("배치보다 많이 유입돼도 반복 입장 처리로 전원이 정확히 한 번씩 입장하고 대기열이 빈다.")
    @Test
    void drainsEntireQueueAcrossRepeatedAdmissions() {
        // given
        int total = 12;
        for (long userId = 1; userId <= total; userId++) {
            waitingQueueRepository.enroll(userId);
        }

        // when - 스케줄러가 배치(5)씩 반복 입장 (큐가 빌 때까지)
        int admittedTotal = 0;
        int perTick;
        do {
            perTick = admissionService.admit(5);
            admittedTotal += perTick;
        } while (perTick > 0);

        // then - 전원 입장(유실 0)·각자 토큰 보유(중복 0)·큐는 빔
        assertThat(admittedTotal).isEqualTo(total);
        assertThat(waitingQueueRepository.size()).isZero();
        for (long userId = 1; userId <= total; userId++) {
            assertThat(entryTokenRepository.find(userId)).isPresent();
        }
    }
}
