package com.loopers.domain.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// TTL 만료를 실제로 검증하기 위해 이 테스트에서만 토큰 TTL 을 1초로 오버라이드한다(운영은 5분). 스케줄러는 test 프로파일에서 이미 off.
@SpringBootTest
@TestPropertySource(properties = "queue.token.ttl=1s")
class EntryTokenStoreIntegrationTest {

    @Autowired
    private EntryTokenStore entryTokenStore;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("발급한 토큰은 조회되고, 발급 값과 동일하다.")
    @Test
    void issue_thenFind_returnsSameToken() {
        // act
        String token = entryTokenStore.issue("alice");

        // assert
        assertThat(entryTokenStore.find("alice")).contains(token);
    }

    @DisplayName("삭제한 토큰은 더 이상 조회되지 않는다.")
    @Test
    void delete_removesToken() {
        // arrange
        entryTokenStore.issue("alice");

        // act
        entryTokenStore.delete("alice");

        // assert
        assertThat(entryTokenStore.find("alice")).isEmpty();
    }

    @DisplayName("TTL 이 지나면 토큰이 자동 만료되어 조회되지 않는다.")
    @Test
    void token_expiresAfterTtl() throws InterruptedException {
        // arrange — TTL 1초
        entryTokenStore.issue("alice");
        assertThat(entryTokenStore.find("alice")).isPresent();

        // act — TTL 초과까지 대기
        Thread.sleep(1_200L);

        // assert — 만료되어 사라짐
        Optional<String> afterExpiry = entryTokenStore.find("alice");
        assertThat(afterExpiry).isEmpty();
    }
}