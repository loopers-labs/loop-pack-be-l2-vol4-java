package com.loopers.ranking.interfaces;

import com.loopers.ranking.application.RankingCarryOverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 매일 23:50(KST)에 오늘 판을 내일 판으로 carry-over 한다.
 * 여러 인스턴스가 떠도 Redis 락으로 한 대만 수행한다(락 TTL 로 자동 해제).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final String LOCK_KEY = "ranking:carry-over-lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final RankingCarryOverService rankingCarryOverService;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        if (!acquireLock()) {
            return; // 다른 인스턴스가 수행
        }
        try {
            rankingCarryOverService.seedNextDay();
            log.info("ranking carry-over done");
        } catch (Exception e) {
            log.error("ranking carry-over failed", e);
        }
    }

    private boolean acquireLock() {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }
}
