package com.loopers.domain.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EntryTokenServiceIntegrationTest {

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("토큰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("발급한 토큰은 같은 유저의 validate를 통과한다.")
        @Test
        void issuedToken_passesValidate() {
            // act
            String token = entryTokenService.issue(1L);

            // assert
            assertThat(entryTokenService.validate(1L, token)).isTrue();
        }

        @DisplayName("발급받은 값과 다른 토큰을 제시하면 validate에 실패한다.")
        @Test
        void validateFails_whenTokenMismatch() {
            // arrange
            entryTokenService.issue(1L);

            // act & assert
            assertThat(entryTokenService.validate(1L, "wrong-token")).isFalse();
        }

        @DisplayName("토큰을 발급받은 적 없는 유저는 validate에 실패한다.")
        @Test
        void validateFails_whenNoToken() {
            // act & assert
            assertThat(entryTokenService.validate(999L, "any-token")).isFalse();
        }
    }

    @DisplayName("토큰 수명(TTL) 관점에서,")
    @Nested
    class Expiry {

        @DisplayName("TTL이 지나면 토큰이 자동 삭제되어 validate에 실패한다.")
        @Test
        void validateFails_afterTtlExpires() throws InterruptedException {
            // arrange: TTL 1초짜리 서비스로 실제 Redis 만료를 검증한다.
            EntryTokenService shortLived = new EntryTokenService(entryTokenRepository, Duration.ofSeconds(1));
            String token = shortLived.issue(1L);
            assertThat(shortLived.validate(1L, token)).isTrue();

            // act: TTL 초과 대기
            Thread.sleep(1_500);

            // assert: Redis가 키를 자동 삭제 → GET null → validate 실패
            assertThat(shortLived.validate(1L, token)).isFalse();
        }
    }
}
