package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueDomainService;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueService {

    private final QueueRepository queueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final QueueDomainService queueDomainService;

    public QueueInfo enter(Long userId) {
        long rank = queueRepository.enter(userId, System.currentTimeMillis());
        return QueueInfo.waiting(rank, queueDomainService.estimateWaitSeconds(rank));
    }

    public QueueInfo position(Long userId) {
        Optional<String> token = entryTokenRepository.find(userId);
        if (token.isPresent()) {
            return QueueInfo.ready(token.get());
        }

        long rank = queueRepository.rank(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 기록이 없습니다."));
        return QueueInfo.waiting(rank, queueDomainService.estimateWaitSeconds(rank));
    }

    public boolean validateToken(Long userId, String token) {
        return entryTokenRepository.find(userId)
            .filter(stored -> stored.equals(token))
            .isPresent();
    }

    public void deleteToken(Long userId) {
        entryTokenRepository.delete(userId);
    }
}
