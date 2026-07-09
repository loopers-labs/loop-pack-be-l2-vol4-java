package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class EntryTokenRepositoryImplIntegrationTest {

    private static final String ENTRY_TOKEN_KEY_PREFIX = "queue:entry-token:";

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("입장 토큰을 조회할 때,")
    @Nested
    class Find {

        @DisplayName("세팅된 토큰을 그대로 조회한다.")
        @Test
        void find_returnsSetToken_whenTokenExists() {
            // given
            Long userId = 1L;
            redisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + userId, "abc-123");

            // when
            Optional<String> token = entryTokenRepository.find(userId);

            // then
            assertThat(token).contains("abc-123");
        }

        @DisplayName("토큰이 없는 유저를 조회하면 빈 값을 반환한다.")
        @Test
        void find_returnsEmpty_whenTokenDoesNotExist() {
            // given
            Long userId = 999L;

            // when
            Optional<String> token = entryTokenRepository.find(userId);

            // then
            assertThat(token).isEmpty();
        }
    }

    @DisplayName("입장 토큰을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("삭제 후에는 해당 유저의 토큰을 조회할 수 없다.")
        @Test
        void delete_removesToken_soFindReturnsEmptyAfterward() {
            // given
            Long userId = 1L;
            redisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + userId, "abc-123");

            // when
            entryTokenRepository.delete(userId);

            // then
            assertThat(entryTokenRepository.find(userId)).isEmpty();
        }
    }

    @DisplayName("입장 토큰 TTL이 만료됐을 때,")
    @Nested
    class Expiration {

        @DisplayName("설정한 TTL이 지나면 토큰이 사라져 find가 빈 값을 반환한다.")
        @Test
        void find_returnsEmpty_afterTtlExpires() throws InterruptedException {
            // given
            Long userId = 1L;
            redisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + userId, "abc-123", Duration.ofMillis(300));

            // when
            Thread.sleep(500);

            // then
            assertThat(entryTokenRepository.find(userId)).isEmpty();
        }

        @DisplayName("TTL이 만료된 토큰으로 검증을 시도하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void verify_throwsForbidden_afterTokenExpires() throws InterruptedException {
            // given
            Long userId = 1L;
            redisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + userId, "abc-123", Duration.ofMillis(300));
            Thread.sleep(500);

            // when
            CoreException result = assertThrows(CoreException.class, () -> entryTokenService.verify(userId, "abc-123"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
