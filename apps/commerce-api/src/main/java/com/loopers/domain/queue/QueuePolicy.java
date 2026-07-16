package com.loopers.domain.queue;

import com.loopers.support.config.QueueProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대기열 응답 정책 — 순번으로부터 예상 대기 시간과 다음 폴링 시점을 계산하는 순수 로직.
 *
 * <p><b>예상 대기 시간</b> = ceil(순번 / 발급 TPS). 발급 TPS 는 스케줄러 설정(batch-size × 1000/interval-ms)에서
 * 유도한다 — 별도 설정으로 두면 스케줄러 튜닝 시 두 값이 어긋난다(drift). 토큰 만료·이탈로 흔들리는
 * 추정값이므로 호출자는 "약 N초"로 표현해야 한다.</p>
 *
 * <p><b>폴링 주기</b>는 순번 구간별로 다르다 — 뒷순번 유저는 자주 조회해도 얻는 정보가 없고,
 * 앞순번 유저는 토큰 수신 지연이 곧 TTL 낭비다. 주기를 서버 응답(pollAfterMillis)으로 내려
 * 폴링 부하의 제어권을 서버가 갖는다(클라이언트 하드코딩이면 운영 중 조절 수단이 배포뿐).</p>
 */
@Component
public class QueuePolicy {

    private final double issueRatePerSecond;
    private final List<QueueProperties.Polling.Band> bands;
    private final long defaultIntervalMs;

    public QueuePolicy(QueueProperties properties) {
        this.issueRatePerSecond = properties.scheduler().issueRatePerSecond();
        this.bands = properties.polling().bands();
        this.defaultIntervalMs = properties.polling().defaultIntervalMs();
    }

    /** 1-based 순번 기준 예상 대기 시간(초). */
    public long estimatedWaitSeconds(long position) {
        return (long) Math.ceil(position / issueRatePerSecond);
    }

    /** 1-based 순번 기준 다음 폴링까지의 간격(ms). 밴드는 maxPosition 오름차순으로 평가한다. */
    public long pollAfterMillis(long position) {
        return bands.stream()
                .filter(band -> position <= band.maxPosition())
                .findFirst()
                .map(QueueProperties.Polling.Band::intervalMs)
                .orElse(defaultIntervalMs);
    }
}
