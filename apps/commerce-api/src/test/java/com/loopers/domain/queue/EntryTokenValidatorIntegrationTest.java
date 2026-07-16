package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EntryTokenValidatorIntegrationTest {

    private final EntryTokenValidator entryTokenValidator;
    private final EntryTokenRepository entryTokenRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public EntryTokenValidatorIntegrationTest(
        EntryTokenValidator entryTokenValidator,
        EntryTokenRepository entryTokenRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.entryTokenValidator = entryTokenValidator;
        this.entryTokenRepository = entryTokenRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("유효한 입장 토큰을 가진 유저는 검증을 통과한다.")
    @Test
    void passesWhenUserHasLiveEntryToken() {
        // given
        entryTokenRepository.issue(100L, Duration.ofMinutes(5));

        // when & then
        assertThatCode(() -> entryTokenValidator.validate(100L)).doesNotThrowAnyException();
    }

    @DisplayName("입장 토큰이 없는 유저의 검증은 ENTRY_TOKEN_INVALID 로 거부된다.")
    @Test
    void rejectsWhenUserHasNoEntryToken() {
        // given

        // when
        CoreException exception = assertThrows(CoreException.class, () -> entryTokenValidator.validate(100L));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
    }

    @DisplayName("입장 토큰이 만료된 유저의 검증은 ENTRY_TOKEN_INVALID 로 거부된다.")
    @Test
    void rejectsWhenEntryTokenHasExpired() throws InterruptedException {
        // given
        entryTokenRepository.issue(100L, Duration.ofMillis(300));
        Thread.sleep(500);

        // when
        CoreException exception = assertThrows(CoreException.class, () -> entryTokenValidator.validate(100L));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);
    }
}
