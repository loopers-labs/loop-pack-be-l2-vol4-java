package com.loopers.domain.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class EntryTokenService {

    private final EntryTokenRepository entryTokenRepository;
    private final Duration ttl;

    @Autowired
    public EntryTokenService(
        EntryTokenRepository entryTokenRepository,
        @Value("${queue.entry-token.ttl-seconds:300}") long ttlSeconds
    ) {
        this(entryTokenRepository, Duration.ofSeconds(ttlSeconds));
    }

    // 테스트에서 짧은 TTL을 주입하기 위한 생성자.
    public EntryTokenService(EntryTokenRepository entryTokenRepository, Duration ttl) {
        this.entryTokenRepository = entryTokenRepository;
        this.ttl = ttl;
    }

    /** UUID 토큰을 생성해 TTL과 함께 저장하고 반환한다. */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        entryTokenRepository.save(userId, token, ttl);
        return token;
    }

    /** 저장된 토큰과 제시된 토큰이 일치하는지 검증한다(존재 확인이 아니라 값 일치). */
    public boolean validate(Long userId, String token) {
        if (token == null) {
            return false;
        }
        return entryTokenRepository.find(userId)
            .map(token::equals)
            .orElse(false);
    }
}
