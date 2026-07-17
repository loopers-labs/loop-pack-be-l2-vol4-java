package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class EntryTokenService {

    private final EntryTokenRepository entryTokenRepository;

    public Optional<String> find(Long userId) {
        return entryTokenRepository.find(userId);
    }

    public void verify(Long userId, String token) {
        String storedToken = find(userId)
                .orElseThrow(() -> new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 없거나 만료되었습니다. 대기열을 통해 다시 진입해주세요."));
        if (!storedToken.equals(token)) {
            throw new CoreException(ErrorType.FORBIDDEN, "유효하지 않은 입장 토큰입니다. 대기열을 통해 다시 진입해주세요.");
        }
    }

    public void consume(Long userId) {
        entryTokenRepository.delete(userId);
    }
}
