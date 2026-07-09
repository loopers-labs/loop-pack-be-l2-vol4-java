package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EntryTokenRepositoryImplIntegrationTest {

    private static final String ENTRY_TOKEN_KEY_PREFIX = "queue:entry-token:";

    @Autowired
    private EntryTokenRepository entryTokenRepository;

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
}
