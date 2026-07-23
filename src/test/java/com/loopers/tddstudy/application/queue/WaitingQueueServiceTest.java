package com.loopers.tddstudy.application.queue;

import com.loopers.tddstudy.support.FakeWaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.loopers.tddstudy.support.FakeEntryTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingQueueServiceTest {

    private FakeWaitingQueueRepository repo;
    private FakeEntryTokenRepository tokenRepo;
    private WaitingQueueService service;

    @BeforeEach
    void setUp() {
        repo = new FakeWaitingQueueRepository();
        tokenRepo = new FakeEntryTokenRepository();
        EntryTokenService tokenService = new EntryTokenService(tokenRepo, 300);
        service = new WaitingQueueService(repo, tokenService, 10);
    }

    @Test
    @DisplayName("먼저 진입한 유저가 앞 순번을 받는다")
    void order_by_entry() {
        repo.enqueue(10L, 1000);
        repo.enqueue(20L, 1001);
        repo.enqueue(30L, 1002);

        assertThat(service.position(10L).position()).isEqualTo(1);
        assertThat(service.position(30L).position()).isEqualTo(3);
        assertThat(service.position(30L).totalWaiting()).isEqualTo(3);
    }

    @Test
    @DisplayName("중복 진입해도 최초 순번이 유지된다 (NX)")
    void duplicate_enter_keeps_rank() {
        repo.enqueue(10L, 1000);
        repo.enqueue(20L, 1001);

        boolean again = repo.enqueue(10L, 9999);   // 이미 있음 → 무시

        assertThat(again).isFalse();
        assertThat(service.position(10L).position()).isEqualTo(1);  // 여전히 1등
    }

    @Test
    @DisplayName("예상 대기 시간 = 앞사람 수 / 처리량")
    void estimated_wait() {
        for (long i = 1; i <= 25; i++) repo.enqueue(i, 1000 + i);   // 25명

        // 25번째 유저: 앞에 24명, 처리량 10/s → 24/10 = 2초
        assertThat(service.position(25L).estimatedWaitSeconds()).isEqualTo(2);
    }

    @Test
    @DisplayName("대기열에 없으면 inQueue=false")
    void not_in_queue() {
        assertThat(service.position(99L).inQueue()).isFalse();
    }
}
