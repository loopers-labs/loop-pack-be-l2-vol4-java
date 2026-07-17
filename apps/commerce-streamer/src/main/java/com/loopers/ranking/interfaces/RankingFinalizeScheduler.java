package com.loopers.ranking.interfaces;

import com.loopers.ranking.application.RankingFinalizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 매일 00:30(KST)에 어제 판을 스냅샷으로 확정한다. grace 30분은 자정 넘어 늦게 도착한
 * (occurredAt 이 어제인) 이벤트가 어제 ZSET 에 다 반영될 시간을 준다. Redis 락으로 한 대만 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingFinalizeScheduler {

    private static final String LOCK_KEY = "ranking:finalize-lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RankingFinalizeService rankingFinalizeService;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    public void finalizeYesterday() {
        if (!acquireLock()) {
            return; // 다른 인스턴스가 수행
        }
        try {
            rankingFinalizeService.finalizeYesterday();
            log.info("ranking finalize done");
        } catch (Exception e) {
            log.error("ranking finalize failed", e);
        }
    }

    private boolean acquireLock() {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }
}
