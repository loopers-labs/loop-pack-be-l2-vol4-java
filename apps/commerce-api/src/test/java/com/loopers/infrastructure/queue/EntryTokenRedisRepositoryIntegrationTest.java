package com.loopers.infrastructure.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// 대기열 테스트들과 같은 컨텍스트를 공유하도록 동일 프로퍼티 사용 (이 테스트 자체는 스케줄러 영향 없음)
@SpringBootTest(properties = "queue.scheduler.enabled=false")
class EntryTokenRedisRepositoryIntegrationTest {

    @Autowired private EntryTokenRedisRepository entryTokenRepository;
    @Autowired private RedisCleanUp redisCleanUp;

    private static final Long USER_ID = 1L;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("토큰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("발급된 토큰으로 검증하면 유효하다.")
        @Test
        void issuedTokenIsValid() {
            // arrange & act
            String token = entryTokenRepository.issue(USER_ID, Duration.ofMinutes(5));

            // assert
            assertThat(entryTokenRepository.isValid(USER_ID, token)).isTrue();
        }

        @DisplayName("TTL이 지나면 토큰이 만료되어 무효화된다.")
        @Test
        void tokenExpires_afterTtl() throws InterruptedException {
            // arrange
            String token = entryTokenRepository.issue(USER_ID, Duration.ofMillis(200));

            // act
            Thread.sleep(400);

            // assert
            assertThat(entryTokenRepository.isValid(USER_ID, token)).isFalse();
        }

        @DisplayName("다른 유저의 토큰 값으로 검증하면 무효하다.")
        @Test
        void wrongTokenIsInvalid() {
            // arrange
            entryTokenRepository.issue(USER_ID, Duration.ofMinutes(5));

            // act & assert
            assertThat(entryTokenRepository.isValid(USER_ID, "wrong-token")).isFalse();
        }
    }

    @DisplayName("토큰 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("삭제 후에는 해당 토큰이 더 이상 유효하지 않다.")
        @Test
        void tokenInvalid_afterDelete() {
            // arrange
            String token = entryTokenRepository.issue(USER_ID, Duration.ofMinutes(5));

            // act
            entryTokenRepository.delete(USER_ID);

            // assert
            assertThat(entryTokenRepository.isValid(USER_ID, token)).isFalse();
        }
    }
}
