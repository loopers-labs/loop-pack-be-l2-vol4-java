package com.loopers.queue.infrastructure;

import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisEntryTokenStoreIntegrationTest {

    @Autowired
    private EntryTokenStore sut;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("발급하면 find 로 같은 토큰을 조회할 수 있다")
    void givenIssuedToken_whenFind_thenReturnsSameToken() {
        String token = sut.issue("user-1", Duration.ofMinutes(5));

        assertThat(sut.find("user-1")).contains(token);
    }

    @Test
    @DisplayName("발급한 토큰에는 TTL이 설정된다")
    void givenIssuedToken_whenCheckExpire_thenHasTtl() {
        sut.issue("user-1", Duration.ofSeconds(300));

        Long ttl = redisTemplate.getExpire("order-queue:entry-token:user-1", TimeUnit.SECONDS);

        assertThat(ttl).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("삭제하면 find 는 비어 있다")
    void givenIssuedToken_whenRemove_thenFindEmpty() {
        sut.issue("user-1", Duration.ofMinutes(5));

        sut.remove("user-1");

        assertThat(sut.find("user-1")).isEmpty();
    }
}
