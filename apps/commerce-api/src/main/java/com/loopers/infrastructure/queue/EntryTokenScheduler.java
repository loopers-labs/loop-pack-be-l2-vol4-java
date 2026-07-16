package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 대기열에서 앞 N명을 꺼내 입장 토큰을 발급하는 스케줄러 (OutboxRelay 와 같은 @Scheduled 폴링).
 *
 * <p><b>발급 속도가 곧 주문 유입 TPS 다.</b> batch-size × (1000/interval-ms) 가 하류(DB 커넥션 풀)
 * 용량에서 역산한 상한이며, 같은 총량이라도 1초에 몰아 발급하면 Thundering Herd 로 매초 스파이크가
 * 재현되므로 짧은 주기(100ms)로 나눠 평탄화한다.</p>
 *
 * <p>pop 과 토큰 SET 은 원자적이지 않다 — pop(ZPOPMIN)은 이미 확정돼 롤백 경계가 없으므로,
 * issue 예외(catch)나 pop 직후 크래시면 해당 유저는 토큰 없이 대기열에서만 빠진다. 유실 창이
 * 좁고(수 ms) 재진입으로 자가 복구되며, 무엇보다 <b>안전한 방향의 유실</b>이라 단순 구현을 택했다 —
 * 잃은 슬롯은 초과 입장이 아니라 낭비(하류가 덜 씀, 토큰 만료와 같은 결)라 정합성 사고로 번지지
 * 않는다. 크래시 창까지 닫으려면 Lua 원자 pop+issue 가 필요하나 배치 복잡도 대비 실익이 낮다.
 * 대신 실패는 위 error 로그로 관측한다(유실률이 유의미하면 그때 원자화를 재검토). 같은 이유로
 * 발급 실패도 행별로 격리한다(한 명의 실패가 배치를 막지 않게).</p>
 *
 * <p>다중 인스턴스 시 ZPOPMIN 이 원자적이라 중복 발급은 없지만 발급 속도가 인스턴스 수만큼
 * 배가된다 — 단일 인스턴스 전제(분산락은 nice-to-have). {@code queue.enabled=false} 면 빈 자체를
 * 만들지 않아 대기열이 꺼진 환경(test 포함)에서 Redis 를 폴링하지 않는다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "queue.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EntryTokenScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final QueueProperties properties;

    @Scheduled(fixedDelayString = "${queue.scheduler.interval-ms}")
    public void issueTokens() {
        List<Long> admitted = waitingQueueRepository.popFront(properties.scheduler().batchSize());
        for (Long userId : admitted) {
            try {
                entryTokenRepository.issue(
                        userId,
                        UUID.randomUUID().toString(),
                        Duration.ofSeconds(properties.tokenTtlSeconds())
                );
            } catch (Exception e) {
                log.error("입장 토큰 발급 실패 — 유저는 재진입으로 복구 가능. userId={}", userId, e);
            }
        }
    }
}
