package com.loopers.tddstudy.application.queue;

import com.loopers.tddstudy.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EntryTokenService {

    private final EntryTokenRepository tokenRepository;
    private final long ttlSeconds;

    public EntryTokenService(EntryTokenRepository tokenRepository,
                             @Value("${queue.token-ttl-seconds:300}") long ttlSeconds) {
        this.tokenRepository = tokenRepository;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();   // 위조 방지용 랜덤
        tokenRepository.issue(userId, token, ttlSeconds);
        return token;
    }

    public String getToken(Long userId) {
        return tokenRepository.find(userId);
    }

    public boolean validateAndConsume(Long userId, String token) {
        return token != null && tokenRepository.consume(userId, token);
    }
}
