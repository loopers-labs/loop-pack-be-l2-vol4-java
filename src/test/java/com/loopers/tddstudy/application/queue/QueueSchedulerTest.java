// src/test/java/com/loopers/tddstudy/application/queue/QueueSchedulerTest.java
package com.loopers.tddstudy.application.queue;

import com.loopers.tddstudy.support.FakeEntryTokenRepository;
import com.loopers.tddstudy.support.FakeWaitingQueueRepository;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

class QueueSchedulerTest {

    @Test @DisplayName("배치 크기만큼만 입장시키고 토큰을 발급한다 (처리량 초과 방어)")
    void admits_only_batch_size() {
        FakeWaitingQueueRepository queue = new FakeWaitingQueueRepository();
        FakeEntryTokenRepository tokenRepo = new FakeEntryTokenRepository();
        EntryTokenService tokenService = new EntryTokenService(tokenRepo, 300);
        for (long i = 1; i <= 500; i++) queue.enqueue(i, 1000 + i);   // 500명 대기

        QueueScheduler scheduler = new QueueScheduler(queue, tokenService, 100);  // 배치 100
        scheduler.admit();

        assertThat(queue.getTotalCount()).isEqualTo(400);        // 100명만 빠짐
        assertThat(tokenService.getToken(1L)).isNotNull();       // 앞사람 토큰 발급됨
        assertThat(tokenService.getToken(500L)).isNull();        // 뒷사람은 아직
    }
}
