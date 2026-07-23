package com.loopers.tddstudy.infrastructure.queue;

import com.loopers.tddstudy.domain.queue.EntryTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest                              // Redis 슬라이스만 로드 (localhost:6379)
@Import(EntryTokenRedisRepository.class)    // 커스텀 @Repository 등록
class EntryTokenTtlIntegrationTest {

    @Autowired
    EntryTokenRepository tokenRepository;

    @Test
    @DisplayName("입장 토큰은 TTL 초과 시 자동 만료되어 검증에 실패한다")
    void token_expires_after_ttl() throws InterruptedException {
        Long userId = 999L;
        tokenRepository.issue(userId, "tok-1", 1);   // TTL 1초

        assertThat(tokenRepository.find(userId)).isEqualTo("tok-1");   // 발급 직후: 유효

        Thread.sleep(1500);                          // TTL 초과 대기

        assertThat(tokenRepository.find(userId)).isNull();              // 자동 만료
        assertThat(tokenRepository.consume(userId, "tok-1")).isFalse(); // 검증 실패
    }
}
