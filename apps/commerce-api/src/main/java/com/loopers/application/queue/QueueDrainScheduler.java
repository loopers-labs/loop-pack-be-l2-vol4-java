package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 대기열을 일정 주기로 드레인하는 엔진. 매 틱 앞에서 N명을 꺼내 입장 토큰을 발급한다.
 * 하류(DB) 용량에 맞춰 조금씩 흘려보내(back-pressure), 1초에 몰아주지 않고 100ms 단위로 쪼개 Thundering Herd 를 완화한다.
 *
 * 처리량 상한(틱당 N명)은 "puller 가 하나"라는 전제에서만 성립한다 — 여러 인스턴스가 동시에 돌면 각자 N명씩 꺼내
 * 2N 이 방출된다. 단일 인스턴스 배포 또는 @ConditionalOnProperty 로 한 곳만 켜서 single puller 를 보장한다.
 */
@Slf4j
@ConditionalOnProperty(name = "queue.drain.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Component
public class QueueDrainScheduler {

    private final EntryTokenRepository entryTokenRepository;

    @Value("${queue.drain.batch-size:18}")
    private int batchSize;

    @Value("${queue.drain.token-ttl-seconds:300}")
    private long tokenTtlSeconds;

    @Scheduled(fixedDelayString = "${queue.drain.interval-ms:100}")
    public void drain() {
        try {
            List<Long> issued = entryTokenRepository.issueToNext(batchSize, Duration.ofSeconds(tokenTtlSeconds));
            if (!issued.isEmpty()) {
                log.debug("대기열 입장 토큰 {}건 발급: {}", issued.size(), issued);
            }
        } catch (Exception e) {
            // 한 틱의 실패가 스케줄러 자체를 멈추지 않도록 격리 — 다음 주기에 재시도한다.
            log.warn("대기열 드레인 실패 — 다음 주기에 재시도합니다.", e);
        }
    }
}
