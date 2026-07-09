package com.loopers.application.outbox;

import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 백로그를 Micrometer 게이지로 노출한다({@code /actuator/prometheus} → Prometheus → Grafana).
 *
 * <ul>
 *   <li>{@code outbox.pending.count} — 발행 대기(PENDING) 수. 하이브리드 즉시발행이 잘 도는지·릴레이가 밀리는지
 *       가늠하는 <b>백로그</b> 지표. 정상 상태에선 0 근처, 브로커 장애/부하 시 증가한다. 즉시발행 on/off 실험의
 *       핵심 관찰 대상.</li>
 *   <li>{@code outbox.failed.count} — 영구 실패(FAILED) 수. 0이 아니면 운영 개입 신호.</li>
 * </ul>
 *
 * <p>게이지 콜백은 스크레이프 시점에 호출되어 {@code count(*)}를 실행한다({@code (status, id)} 인덱스로 저렴).
 * 다중 인스턴스는 각자 같은 전역 count를 보고하므로 Grafana에선 {@code max()}로 집계하면 된다(Prometheus
 * 메트릭명은 {@code outbox_pending_count}/{@code outbox_failed_count}).
 */
@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final OutboxJpaRepository outboxJpaRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void bindGauges() {
        Gauge.builder("outbox.pending.count", outboxJpaRepository, r -> r.countByStatus(OutboxStatus.PENDING))
                .description("발행 대기(PENDING) outbox 메시지 수 — 백로그")
                .register(meterRegistry);
        Gauge.builder("outbox.failed.count", outboxJpaRepository, r -> r.countByStatus(OutboxStatus.FAILED))
                .description("영구 실패(FAILED) outbox 메시지 수 — 운영 개입 신호")
                .register(meterRegistry);
    }
}
