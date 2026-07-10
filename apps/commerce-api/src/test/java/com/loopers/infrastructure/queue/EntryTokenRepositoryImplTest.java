package com.loopers.infrastructure.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class EntryTokenRepositoryImplTest {

    @Autowired EntryTokenRepositoryImpl repository;
    @Autowired RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll(); // 선행 테스트 잔재에 기대지 않는 자기완결 클린업
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("발급한 토큰은 TTL 내에 동일 값으로 조회된다.")
    @Test
    void issueThenFind_roundTrip() {
        // arrange
        repository.issue("userA", "token-1", Duration.ofMinutes(5));

        // act & assert
        assertThat(repository.find("userA")).contains("token-1");
    }

    @DisplayName("발급한 적 없는 유저의 토큰 조회는 empty 다.")
    @Test
    void find_returnsEmpty_whenNeverIssued() {
        // act & assert
        assertThat(repository.find("ghost")).isEmpty();
    }

    @DisplayName("delete 하면 토큰이 삭제되어 더 이상 조회되지 않는다. (1회용)")
    @Test
    void delete_removesToken() {
        // arrange
        repository.issue("userA", "token-1", Duration.ofMinutes(5));

        // act
        repository.delete("userA");

        // assert
        assertThat(repository.find("userA")).isEmpty();
    }

    @DisplayName("TTL 이 지나면 토큰이 자동 만료되어 조회되지 않는다.")
    @Test
    void token_expires_afterTtl() {
        // arrange — 짧은 TTL 로 발급
        repository.issue("userA", "token-1", Duration.ofSeconds(1));
        assertThat(repository.find("userA")).contains("token-1");

        // act & assert — Redis TTL 만료를 기다린다
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(repository.find("userA")).isEmpty());
    }
}
