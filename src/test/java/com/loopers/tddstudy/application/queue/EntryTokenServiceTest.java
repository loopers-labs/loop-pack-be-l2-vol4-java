// src/test/java/com/loopers/tddstudy/application/queue/EntryTokenServiceTest.java
package com.loopers.tddstudy.application.queue;

import com.loopers.tddstudy.support.FakeEntryTokenRepository;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;

class EntryTokenServiceTest {
    private FakeEntryTokenRepository repo;
    private EntryTokenService service;

    @BeforeEach void setUp() { repo = new FakeEntryTokenRepository(); service = new EntryTokenService(repo, 300); }

    @Test @DisplayName("발급한 토큰은 검증 통과하고, 한 번 쓰면 소모된다")
    void issue_then_consume() {
        String token = service.issue(1L);
        assertThat(service.validateAndConsume(1L, token)).isTrue();   // 첫 사용 성공
        assertThat(service.validateAndConsume(1L, token)).isFalse();  // 재사용 불가(소모됨)
    }

    @Test @DisplayName("틀린 토큰은 거부된다")
    void wrong_token() {
        service.issue(1L);
        assertThat(service.validateAndConsume(1L, "wrong")).isFalse();
    }
}
