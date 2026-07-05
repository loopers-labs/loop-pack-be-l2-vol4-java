package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisEntryTokenRepositoryIntegrationTest {

    private final EntryTokenRepository entryTokenRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public RedisEntryTokenRepositoryIntegrationTest(
        EntryTokenRepository entryTokenRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.entryTokenRepository = entryTokenRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("발급한 입장 토큰은 곧바로 같은 값으로 조회된다.")
    @Test
    void issuedTokenIsFound() {
        // given
        EntryToken issued = entryTokenRepository.issue(100L, Duration.ofMinutes(5));

        // when
        Optional<EntryToken> found = entryTokenRepository.find(100L);

        // then
        assertThat(found).contains(issued);
    }

    @DisplayName("TTL 이 지나면 입장 토큰은 만료되어 조회되지 않는다.")
    @Test
    void tokenExpiresAfterTtl() throws InterruptedException {
        // given
        entryTokenRepository.issue(100L, Duration.ofMillis(300));

        // when
        Thread.sleep(500);

        // then
        assertThat(entryTokenRepository.find(100L)).isEmpty();
    }

    @DisplayName("사용 완료로 삭제한 토큰은 더 이상 조회되지 않는다.")
    @Test
    void consumedTokenIsGone() {
        // given
        entryTokenRepository.issue(100L, Duration.ofMinutes(5));

        // when
        entryTokenRepository.consume(100L);

        // then
        assertThat(entryTokenRepository.find(100L)).isEmpty();
    }
}
