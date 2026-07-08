package com.loopers.domain.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// test 프로파일에서 입장 스케줄러가 꺼져 있어(application.yml), admitNext 를 직접 호출해 결정적으로 검증한다.
@SpringBootTest
class QueueAdmissionIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntryTokenStore entryTokenStore;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("admitNext(3) 는 대기열 앞 3명을 꺼내 토큰을 발급하고, 나머지는 대기열에 남는다.")
    @Test
    void admitNext_issuesTokensForFront_andLeavesRest() {
        // arrange — 5명 진입 (a=1 .. e=5)
        for (String loginId : new String[]{"a", "b", "c", "d", "e"}) {
            queueService.enter(loginId);
        }

        // act — 앞 3명 입장
        queueService.admitNext(3);

        // assert — 앞 3명은 토큰 보유, 뒤 2명은 미보유
        assertThat(entryTokenStore.find("a")).isPresent();
        assertThat(entryTokenStore.find("b")).isPresent();
        assertThat(entryTokenStore.find("c")).isPresent();
        assertThat(entryTokenStore.find("d")).isEmpty();
        assertThat(entryTokenStore.find("e")).isEmpty();

        // 대기열은 2명(d, e) 잔존, d 가 앞 순번
        assertThat(queueService.getWaitingCount()).isEqualTo(2L);
        assertThat(queueService.getPosition("d")).isEqualTo(1L);
        assertThat(queueService.getPosition("e")).isEqualTo(2L);
    }

    @DisplayName("대기열보다 큰 배치로 admitNext 를 호출해도, 있는 인원만 발급하고 예외 없이 대기열을 비운다.")
    @Test
    void admitNext_withBatchLargerThanQueue_drainsAll() {
        // arrange
        queueService.enter("a");
        queueService.enter("b");

        // act — batch(18) > 대기 인원(2)
        queueService.admitNext(18);

        // assert
        assertThat(entryTokenStore.find("a")).isPresent();
        assertThat(entryTokenStore.find("b")).isPresent();
        assertThat(queueService.getWaitingCount()).isZero();
    }
}